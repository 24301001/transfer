package com.transfer.service;

import com.transfer.adapter.DeepSeekClient;
import com.transfer.adapter.PredictionClient;
import com.transfer.adapter.PredictionOutcome;
import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CreateIncidentRequest;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.PredictionRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            Files.copy(file.getInputStream(), target);

            IncidentAttachment attachment = new IncidentAttachment();
            attachment.setIncidentId(incident.getId());
            attachment.setFileName(fileName);
            attachment.setOriginalFilename(file.getOriginalFilename() == null ? fileName : file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setFilePath(target.toString());
            attachment.setUploadedBy(uploadedBy);
            IncidentAttachment saved = attachmentRepository.save(attachment);
            operationLogService.record(uploadedBy, "UPLOAD_ATTACHMENT", "Incident", incident.getId().toString(), null, saved.getOriginalFilename());
            return saved;
        } catch (IOException ex) {
            throw new BadRequestException("Failed to save attachment: " + ex.getMessage());
        }
    }

    public PredictionResult predict(PredictionRequest request) {
        PredictionRequest normalized = normalizePredictionRequest(request);
        Incident incident = findIncident(normalized.incidentId());
        PredictionOutcome outcome = predictionClient.predict(normalized);
        String explanation = deepSeekClient.explain(outcome, incident.getLocationName(), incident.getDescription());

        PredictionResult result = new PredictionResult();
        result.setIncidentId(incident.getId());
        result.setAccidentType(outcome.accidentType());
        result.setRiskLevel(outcome.riskLevel());
        result.setCongestionDurationMinutes(outcome.congestionDurationMinutes());
        result.setRecoveryDurationMinutes(outcome.recoveryDurationMinutes());
        result.setConfidence(outcome.confidence());
        result.setModelVersion(outcome.modelVersion());
        result.setSuggestions(outcome.suggestions());
        result.setExplanation(explanation);
        PredictionResult saved = predictionResultRepository.save(result);

        incident.setConfirmedAccidentType(outcome.accidentType());
        incident.setRiskLevel(outcome.riskLevel());
        incident.setPredictedCongestionMinutes(outcome.congestionDurationMinutes());
        incident.setPredictedRecoveryMinutes(outcome.recoveryDurationMinutes());
        incident.setConfidence(outcome.confidence());
        incident.setSuggestion(outcome.suggestions());
        incident.setExplanation(explanation);
        incident.setStatus(IncidentStatus.PREDICTED);
        incidentRepository.save(incident);

        operationLogService.record(null, "PREDICT_INCIDENT", "Incident", incident.getId().toString(), null, outcome.riskLevel().name());
        realtimeService.publish("INCIDENT_PREDICTED", Map.of(
                "incidentId", incident.getId(),
                "incidentNo", incident.getIncidentNo(),
                "riskLevel", outcome.riskLevel(),
                "congestionDurationMinutes", outcome.congestionDurationMinutes()
        ));
        return saved;
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

    private PredictionRequest normalizePredictionRequest(PredictionRequest request) {
        if (request == null || request.incidentId() == null) {
            throw new BadRequestException("incidentId is required for prediction");
        }
        Incident incident = findIncident(request.incidentId());
        return new PredictionRequest(
                incident.getId(),
                firstNonBlank(request.locationName(), incident.getLocationName()),
                firstNonBlank(request.roadName(), incident.getRoadName()),
                firstNonBlank(request.accidentType(), incident.getInitialAccidentType()),
                firstNonBlank(request.description(), incident.getDescription()),
                request.occupiedLanes() != null ? request.occupiedLanes() : incident.getOccupiedLanes(),
                request.trafficFlow() != null ? request.trafficFlow() : incident.getTrafficFlow(),
                firstNonBlank(request.weather(), incident.getWeather()),
                firstNonBlank(request.roadLevel(), incident.getRoadLevel())
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
}
