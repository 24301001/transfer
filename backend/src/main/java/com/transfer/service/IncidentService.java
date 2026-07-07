package com.transfer.service;

import com.transfer.adapter.DeepSeekClient;
import com.transfer.adapter.PredictionClient;
import com.transfer.adapter.PredictionOutcome;
import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CreateIncidentRequest;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.PredictionAttachmentPayload;
import com.transfer.dto.PredictionDisplayResponse;
import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.dto.PredictionRequest;
import com.transfer.dto.PredictionSubmitResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.IncidentAttachmentRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.PredictionResultRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final PredictionClient predictionClient;
    private final DeepSeekClient deepSeekClient;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;
    private final Path uploadDir;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentAttachmentRepository attachmentRepository,
            PredictionResultRepository predictionResultRepository,
            DispatchTaskRepository dispatchTaskRepository,
            PredictionClient predictionClient,
            DeepSeekClient deepSeekClient,
            OperationLogService operationLogService,
            RealtimeService realtimeService,
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.incidentRepository = incidentRepository;
        this.attachmentRepository = attachmentRepository;
        this.predictionResultRepository = predictionResultRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.predictionClient = predictionClient;
        this.deepSeekClient = deepSeekClient;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Incident create(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setIncidentNo(generateIncidentNo());
        incident.setLocationName(request.locationName());
        incident.setAddress(request.address());
        incident.setLongitude(request.longitude());
        incident.setLatitude(request.latitude());
        incident.setRoadName(request.roadName());
        incident.setInitialAccidentType(request.initialAccidentType());
        incident.setDescription(request.description());
        incident.setOccupiedLanes(request.occupiedLanes());
        incident.setTrafficFlow(request.trafficFlow());
        incident.setWeather(request.weather());
        incident.setRoadLevel(request.roadLevel());
        incident.setReportUserId(request.reportUserId());
        Incident saved = incidentRepository.save(incident);
        operationLogService.record(request.reportUserId(), "CREATE_INCIDENT", "Incident", saved.getId().toString(), null, saved.getIncidentNo());
        realtimeService.publish("INCIDENT_REPORTED", Map.of("incidentId", saved.getId(), "incidentNo", saved.getIncidentNo()));
        return saved;
    }

    public IncidentDetailResponse createWithAttachments(CreateIncidentRequest request, List<MultipartFile> files) {
        Incident incident = create(request);
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploadAttachment(incident.getId(), file, request.reportUserId());
                }
            }
        }
        return findDetail(incident.getId());
    }

    public List<IncidentAttachment> uploadAttachments(Long incidentId, List<MultipartFile> files, Long uploadedBy) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one attachment file is required");
        }
        List<IncidentAttachment> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                saved.add(uploadAttachment(incidentId, file, uploadedBy));
            }
        }
        if (saved.isEmpty()) {
            throw new BadRequestException("At least one non-empty attachment file is required");
        }
        return saved;
    }

    public IncidentAttachment uploadAttachment(Long incidentId, MultipartFile file, Long uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }
        Incident incident = findIncident(incidentId);
        try {
            Files.createDirectories(uploadDir);
            String extension = resolveExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;
            Path target = uploadDir.resolve(fileName).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            IncidentAttachment attachment = new IncidentAttachment();
            attachment.setIncidentId(incident.getId());
            attachment.setFileName(fileName);
            attachment.setOriginalFilename(file.getOriginalFilename() == null ? fileName : file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setFilePath(target.toString());
            attachment.setUploadedBy(uploadedBy);
            attachment.setRecognitionStatus("WAITING_DATA_MODULE");
            IncidentAttachment saved = attachmentRepository.save(attachment);
            operationLogService.record(uploadedBy, "UPLOAD_ATTACHMENT", "Incident", incident.getId().toString(), null, saved.getOriginalFilename());
            return saved;
        } catch (IOException ex) {
            throw new BadRequestException("Failed to save attachment: " + ex.getMessage());
        }
    }

    public PredictionSubmitResponse submitPredictionRequest(Long incidentId, Long operatorUserId) {
        Incident incident = findIncident(incidentId);
        List<IncidentAttachment> attachments = attachmentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        PredictionRequest request = buildPredictionRequest(incident, attachments);
        PredictionSubmitResponse response = predictionClient.submit(request);
        if (Boolean.TRUE.equals(response.submitted())) {
            incident.setStatus(IncidentStatus.PREDICTION_REQUESTED);
            incidentRepository.save(incident);
            operationLogService.record(operatorUserId, "SUBMIT_PREDICTION_REQUEST", "Incident", incident.getId().toString(), null, response.dataModuleTraceId());
            realtimeService.publish("INCIDENT_PREDICTION_REQUESTED", Map.of(
                    "incidentId", incident.getId(),
                    "incidentNo", incident.getIncidentNo(),
                    "dataModuleTraceId", response.dataModuleTraceId() == null ? "" : response.dataModuleTraceId()
            ));
        }
        return response;
    }

    public PredictionResult acceptPredictionResult(Long incidentId, PredictionModuleResultRequest request, Long operatorUserId) {
        if (request == null) {
            throw new BadRequestException("Prediction module result is required");
        }
        validatePredictionResult(request);
        Incident incident = findIncident(incidentId);
        String riskFactors = joinRiskFactors(request.riskFactors());
        String suggestions = firstNonBlank(request.suggestion(), buildDispositionSuggestion(incident, request, riskFactors));
        PredictionOutcome outcome = new PredictionOutcome(
        request.accidentType(),
        request.riskLevel(),
        request.congestionDurationMinutes(),
        request.recoveryDurationMinutes(),
        request.confidence(),
        firstNonBlank(request.modelVersion(), "data-module"),
        suggestions,
        request.riskFactors(),
        request.evidenceSummary()
);
        String explanation = firstNonBlank(
                request.explanation(),
                deepSeekClient.explain(outcome, incident.getLocationName(), incident.getDescription())
        );

        PredictionResult result = new PredictionResult();
        result.setIncidentId(incident.getId());
        result.setAccidentType(request.accidentType());
        result.setRiskLevel(request.riskLevel());
        result.setCongestionDurationMinutes(request.congestionDurationMinutes());
        result.setRecoveryDurationMinutes(request.recoveryDurationMinutes());
        result.setConfidence(request.confidence());
        result.setModelVersion(outcome.modelVersion());
        result.setSuggestions(suggestions);
        result.setExplanation(explanation);
        result.setRiskFactors(riskFactors);
        result.setEvidenceSummary(request.evidenceSummary());
        result.setDataModuleTraceId(request.dataModuleTraceId());
        result.setRawResult(request.rawResult());
        PredictionResult saved = predictionResultRepository.save(result);

        incident.setConfirmedAccidentType(saved.getAccidentType());
        incident.setRiskLevel(saved.getRiskLevel());
        incident.setPredictedCongestionMinutes(saved.getCongestionDurationMinutes());
        incident.setPredictedRecoveryMinutes(saved.getRecoveryDurationMinutes());
        incident.setConfidence(saved.getConfidence());
        incident.setSuggestion(saved.getSuggestions());
        incident.setExplanation(saved.getExplanation());
        incident.setStatus(IncidentStatus.PREDICTED);
        incidentRepository.save(incident);

        operationLogService.record(operatorUserId, "ACCEPT_PREDICTION_RESULT", "Incident", incident.getId().toString(), null, saved.getRiskLevel().name());
        realtimeService.publish("INCIDENT_PREDICTED", Map.of(
                "incidentId", incident.getId(),
                "incidentNo", incident.getIncidentNo(),
                "accidentType", saved.getAccidentType(),
                "riskLevel", saved.getRiskLevel(),
                "congestionDurationMinutes", saved.getCongestionDurationMinutes(),
                "recoveryDurationMinutes", saved.getRecoveryDurationMinutes()
        ));
        return saved;
    }

    public IncidentAttachment findAttachment(Long incidentId, Long attachmentId) {
        Incident incident = findIncident(incidentId);
        IncidentAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!incident.getId().equals(attachment.getIncidentId())) {
            throw new ResourceNotFoundException("Attachment not found for incident: " + incidentId);
        }
        return attachment;
    }

    public Resource loadAttachmentFile(IncidentAttachment attachment) {
        try {
            Path path = Path.of(attachment.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found: " + attachment.getId());
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new BadRequestException("Invalid attachment file path");
        }
    }

    public PredictionDisplayResponse findLatestPrediction(Long incidentId) {
        Incident incident = findIncident(incidentId);
        PredictionResult result = predictionResultRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Prediction result not found for incident: " + incidentId));
        return PredictionDisplayResponse.from(incident, result);
    }

    public Page<Incident> findAll(IncidentStatus status, RiskLevel riskLevel, String keyword, Pageable pageable) {
        return incidentRepository.findAll(buildSpecification(status, riskLevel, keyword), pageable);
    }

    public IncidentDetailResponse findDetail(Long incidentId) {
        Incident incident = findIncident(incidentId);
        return new IncidentDetailResponse(
                incident,
                attachmentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId),
                predictionResultRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId),
                dispatchTaskRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
        );
    }

    public Incident updateStatus(Long incidentId, IncidentStatus status) {
        Incident incident = findIncident(incidentId);
        incident.setStatus(status);
        return incidentRepository.save(incident);
    }

    public Incident findIncident(Long incidentId) {
        if (incidentId == null) {
            throw new BadRequestException("incidentId is required");
        }
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
    }

    private PredictionRequest buildPredictionRequest(Incident incident, List<IncidentAttachment> attachments) {
        return new PredictionRequest(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getLocationName(),
                incident.getAddress(),
                incident.getLongitude(),
                incident.getLatitude(),
                incident.getRoadName(),
                incident.getInitialAccidentType(),
                incident.getDescription(),
                incident.getOccupiedLanes(),
                incident.getTrafficFlow(),
                incident.getWeather(),
                incident.getRoadLevel(),
                attachments.stream()
                        .map(attachment -> new PredictionAttachmentPayload(
                                attachment.getId(),
                                attachment.getOriginalFilename(),
                                attachment.getContentType(),
                                attachment.getFileSize(),
                                attachment.getFilePath()
                        ))
                        .toList()
        );
    }

    private Specification<Incident> buildSpecification(IncidentStatus status, RiskLevel riskLevel, String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(criteriaBuilder.equal(root.get("riskLevel"), riskLevel));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("incidentNo")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("locationName")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), like)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String generateIncidentNo() {
        return "ACC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 6);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private void validatePredictionResult(PredictionModuleResultRequest request) {
        if (request.accidentType() == null || request.accidentType().isBlank()) {
            throw new BadRequestException("accidentType is required");
        }
        if (request.riskLevel() == null) {
            throw new BadRequestException("riskLevel is required");
        }
        if (request.congestionDurationMinutes() == null || request.congestionDurationMinutes() < 0) {
            throw new BadRequestException("congestionDurationMinutes must be greater than or equal to 0");
        }
        if (request.recoveryDurationMinutes() == null || request.recoveryDurationMinutes() < 0) {
            throw new BadRequestException("recoveryDurationMinutes must be greater than or equal to 0");
        }
        if (request.recoveryDurationMinutes() < request.congestionDurationMinutes()) {
            throw new BadRequestException("recoveryDurationMinutes should not be less than congestionDurationMinutes");
        }
        if (request.confidence() == null || request.confidence() < 0 || request.confidence() > 1) {
            throw new BadRequestException("confidence must be between 0 and 1");
        }
    }

    private String buildDispositionSuggestion(Incident incident, PredictionModuleResultRequest request, String riskFactors) {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(switch (request.riskLevel()) {
            case LOW -> "低风险：现场交警可设置警示标志，引导车辆减速通过，持续观察现场变化。";
            case MEDIUM -> "中风险：建议保护事故现场，视情况实施局部车道管控，并通知指挥中心关注拥堵变化。";
            case HIGH -> "高风险：建议立即通知指挥中心，安排警力到场，优先疏导受影响车道，并联系清障车辆。";
            case CRITICAL -> "严重风险：建议启动应急处置流程，联动交警、清障、救援和医疗资源，扩大交通管制范围。";
        });
        if (request.accidentType().contains("封闭") || request.accidentType().toLowerCase().contains("block")) {
            joiner.add("事故涉及道路封闭或车道阻断，应优先设置分流路线。");
        }
        if (incident.getOccupiedLanes() != null && incident.getOccupiedLanes() >= 2) {
            joiner.add("占用车道较多，建议提前发布绕行提示。");
        }
        if (riskFactors != null && !riskFactors.isBlank()) {
            joiner.add("主要风险因子：" + riskFactors + "。");
        }
        return joiner.toString();
    }

    private String joinRiskFactors(List<String> riskFactors) {
        if (riskFactors == null || riskFactors.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("、");
        for (String factor : riskFactors) {
            if (factor != null && !factor.isBlank()) {
                joiner.add(factor.trim());
            }
        }
        String result = joiner.toString();
        return result.isBlank() ? null : result;
    }
}