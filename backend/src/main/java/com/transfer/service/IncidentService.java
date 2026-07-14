package com.transfer.service;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.transfer.adapter.DeepSeekClient;
import com.transfer.adapter.PredictionClient;
import com.transfer.adapter.PredictionOutcome;
import com.transfer.adapter.YoloDetectClient;
import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dispatch.DispatchRecommendationClient;
import com.transfer.dispatch.DispatchRecommendationResult;
import com.transfer.dto.CitizenImmediateAdviceResponse;
import com.transfer.dto.CreateIncidentRequest;
import com.transfer.dto.IncidentArrivalEstimateResponse;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.PredictionAttachmentPayload;
import com.transfer.dto.PredictionDisplayResponse;
import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.dto.PredictionRequest;
import com.transfer.dto.PredictionStatusResponse;
import com.transfer.dto.PredictionSubmitResponse;
import com.transfer.dto.PublicIncidentSubmitResponse;
import com.transfer.enums.CoordinateType;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;
import com.transfer.prediction.AccidentTypeResult;
import com.transfer.prediction.PredictionAlgorithmType;
import com.transfer.prediction.PredictionModelClient;
import com.transfer.prediction.PredictionModuleRequest;
import com.transfer.prediction.PredictionModuleResponse;
import com.transfer.prediction.RiskImpactResult;
import com.transfer.recovery.RecoveryRecommendationClient;
import com.transfer.recovery.RecoveryRecommendationResult;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.IncidentAttachmentRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.PredictionResultRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class IncidentService {

    private static final Logger log =
            LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final PredictionClient predictionClient;
    private final PredictionModelClient predictionModelClient;
    private final RecoveryRecommendationClient recoveryRecommendationClient;
    private final DispatchRecommendationClient dispatchRecommendationClient;
    private final DeepSeekClient deepSeekClient;
    private final FormalDispositionPlanService formalDispositionPlanService;
    private final ArrivalEstimateService arrivalEstimateService;
    private final CitizenAiService citizenAiService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;
    private final MapService mapService;
    private final YoloDetectClient yoloDetectClient;
    private final VideoTranscodeService videoTranscodeService;
    private final Path uploadDir;
    private final boolean autoSubmitPredictionOnPublicReport;
    private final float yoloDefaultConf;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentAttachmentRepository attachmentRepository,
            PredictionResultRepository predictionResultRepository,
            DispatchTaskRepository dispatchTaskRepository,
            PredictionClient predictionClient,
            PredictionModelClient predictionModelClient,
            RecoveryRecommendationClient recoveryRecommendationClient,
            DispatchRecommendationClient dispatchRecommendationClient,
            DeepSeekClient deepSeekClient,
            FormalDispositionPlanService formalDispositionPlanService,
            ArrivalEstimateService arrivalEstimateService,
            CitizenAiService citizenAiService,
            OperationLogService operationLogService,
            RealtimeService realtimeService,
            MapService mapService,
            YoloDetectClient yoloDetectClient,
            VideoTranscodeService videoTranscodeService,
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.incident.auto-submit-prediction-on-public-report:true}")
            boolean autoSubmitPredictionOnPublicReport,
            @Value("${app.yolo.default-conf:0.3}") float yoloDefaultConf
    ) {
        this.incidentRepository = incidentRepository;
        this.attachmentRepository = attachmentRepository;
        this.predictionResultRepository = predictionResultRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.predictionClient = predictionClient;
        this.predictionModelClient = predictionModelClient;
        this.recoveryRecommendationClient = recoveryRecommendationClient;
        this.dispatchRecommendationClient = dispatchRecommendationClient;
        this.deepSeekClient = deepSeekClient;
        this.formalDispositionPlanService = formalDispositionPlanService;
        this.arrivalEstimateService = arrivalEstimateService;
        this.citizenAiService = citizenAiService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
        this.mapService = mapService;
        this.yoloDetectClient = yoloDetectClient;
        this.videoTranscodeService = videoTranscodeService;
        this.autoSubmitPredictionOnPublicReport =
                autoSubmitPredictionOnPublicReport;
        this.yoloDefaultConf = yoloDefaultConf;

        this.uploadDir = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 创建事故。
     */
    @Transactional
    public Incident create(CreateIncidentRequest request) {
        if (request == null) {
            throw new BadRequestException(
                    "Incident request is required"
            );
        }

        Incident incident = new Incident();

        incident.setIncidentNo(generateIncidentNo());

        incident.setLocationName(
                request.locationName().trim()
        );

        incident.setAddress(
                trimToNull(request.address())
        );

        incident.setLongitude(
                request.longitude()
        );

        incident.setLatitude(
                request.latitude()
        );

        incident.setCoordinateType(
                request.coordinateType() == null
                        ? CoordinateType.WGS84
                        : request.coordinateType()
        );

        incident.setRoadName(
                trimToNull(request.roadName())
        );

        incident.setInitialAccidentType(
                trimToNull(request.initialAccidentType())
        );

        incident.setSceneLabels(
                normalizeCsvLabels(request.sceneLabels())
        );

        incident.setDescription(
                request.description().trim()
        );

        incident.setOccupiedLanes(
                request.occupiedLanes()
        );

        incident.setTrafficFlow(
                request.trafficFlow()
        );

        incident.setPeopleFlow(
                request.peopleFlow()
        );

        incident.setWeather(
                trimToNull(request.weather())
        );

        incident.setRoadLevel(
                trimToNull(request.roadLevel())
        );

        incident.setRoadStatus(
                trimToNull(request.roadStatus())
        );

        incident.setPeopleInvolved(
                request.peopleInvolved()
        );

        incident.setInjuredCount(
                request.injuredCount()
        );

        incident.setInjuryReported(
                Boolean.TRUE.equals(request.injuryReported())
                        || (request.injuredCount() != null
                        && request.injuredCount() > 0)
        );

        incident.setInjuryEstimate(
                trimToNull(request.injuryEstimate())
        );

        incident.setReportUserId(
                request.reportUserId()
        );

        incident.setCasualtyDetected(
                citizenAiService.hasCasualtyRisk(
                        request.description(),
                        request.initialAccidentType()
                )
        );

        incident.setStatus(
                IncidentStatus.REPORTED
        );

        incident.setSupportDecisionManual(false);

        /*
         * 在预测结果产生前，根据上报内容进行初步支援判断。
         */
        applyAutomaticSupportDecision(incident);

        /*
         * 尝试转换或补充百度地图坐标。
         * 地图服务异常不影响事故的正常创建。
         */
        try {
            mapService.enrichIncident(incident);
        } catch (ExternalServiceException ex) {
            log.warn(
                    "事故 {} 的地图位置解析失败：{}",
                    incident.getIncidentNo(),
                    ex.getMessage()
            );
        }

        arrivalEstimateService.applyEstimate(incident);

        Incident saved =
                incidentRepository.save(incident);

        operationLogService.record(
                request.reportUserId(),
                "CREATE_INCIDENT",
                "Incident",
                saved.getId().toString(),
                null,
                saved.getIncidentNo()
        );

        realtimeService.publish(
                "INCIDENT_REPORTED",
                Map.of(
                        "incidentId",
                        saved.getId(),

                        "incidentNo",
                        saved.getIncidentNo()
                )
        );

        return saved;
    }

    /**
     * 创建事故并同时上传多个附件。
     */
    @Transactional
    public IncidentDetailResponse createWithAttachments(
            CreateIncidentRequest request,
            List<MultipartFile> files
    ) {
        Incident incident = create(request);

        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploadAttachment(
                            incident.getId(),
                            file,
                            request.reportUserId()
                    );
                }
            }
        }

        return findDetail(incident.getId());
    }

    /**
     * 普通市民事故上报入口：保存事故、照片/视频，生成即时安全提示，
     * 并按配置把事故提交给数据预测模块。
     */
    @Transactional
    public PublicIncidentSubmitResponse publicReport(
            CreateIncidentRequest request,
            List<MultipartFile> photos,
            List<MultipartFile> videos,
            MultipartFile video
    ) {
        Incident incident = create(request);

        incident.setTrackingToken(java.util.UUID.randomUUID().toString());
        incident = incidentRepository.save(incident);

        uploadOptionalFiles(
                incident.getId(),
                photos,
                request.reportUserId(),
                "PHOTO"
        );

        uploadOptionalFiles(
                incident.getId(),
                videos,
                request.reportUserId(),
                "VIDEO"
        );

        if (video != null && !video.isEmpty()) {
            uploadAttachment(
                    incident.getId(),
                    video,
                    request.reportUserId(),
                    "VIDEO"
            );
        }

        // ---- 调用 YOLOv5 AI 检测并自动填充事故类型 ----
        List<IncidentAttachment> allAttachments =
                attachmentRepository.findByIncidentId(incident.getId());
        runYoloDetection(incident, allAttachments, photos, videos, video);

        /*
         * 生成即时安全提示（AI）。
         * 异常时使用保底文案，不阻断事故上报流程。
         */
        CitizenImmediateAdviceResponse immediateAdvice = null;
        try {
            immediateAdvice =
                    citizenAiService.generateImmediateAdvice(incident);
        } catch (Exception e) {
            log.warn(
                    "生成即时安全提示失败: incidentId={}, error={}",
                    incident.getId(),
                    e.getMessage()
            );
        }

        if (immediateAdvice != null) {
            incident.setCitizenImmediateAdvice(
                    immediateAdvice.immediateAdvice()
            );
            incident.setCasualtyDetected(
                    Boolean.TRUE.equals(
                            immediateAdvice.casualtyDetected()
                    )
            );
        } else {
            incident.setCitizenImmediateAdvice(
                    "信息已提交，请先保证自身安全，保持手机畅通。"
            );
        }
        incident = incidentRepository.save(incident);

        /*
         * 提交数据预测模块。
         * 异常时返回 null，不阻断事故上报流程。
         */
        PredictionSubmitResponse predictionSubmit = null;
        if (autoSubmitPredictionOnPublicReport) {
            try {
                predictionSubmit = submitPredictionRequest(
                        incident.getId(),
                        request.reportUserId()
                );
            } catch (Exception e) {
                log.error(
                        "提交预测请求失败: incidentId={}, error={}",
                        incident.getId(),
                        e.getMessage()
                );
            }
        }

        return new PublicIncidentSubmitResponse(
                findDetail(incident.getId()),
                immediateAdvice,
                incident.getEstimatedPoliceArrivalMinutes(),
                incident.getPoliceArrivalText(),
                predictionSubmit,
                incident.getTrackingToken()
        );
    }

    /**
     * 查询事故的预计交警到达时间。
     */
    @Transactional
    public IncidentArrivalEstimateResponse findArrivalEstimate(
            Long incidentId
    ) {
        Incident incident = findIncident(incidentId);
        IncidentArrivalEstimateResponse response =
                arrivalEstimateService.estimateFor(incident);

        incident.setEstimatedPoliceArrivalMinutes(
                response.estimatedPoliceArrivalMinutes()
        );
        incident.setPoliceArrivalText(
                response.estimatedPoliceArrivalText()
        );
        incidentRepository.save(incident);

        return response;
    }

    /**
     * 为一个事故批量上传附件。
     */
    @Transactional
    public List<IncidentAttachment> uploadAttachments(
            Long incidentId,
            List<MultipartFile> files,
            Long uploadedBy
    ) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException(
                    "At least one attachment file is required"
            );
        }

        List<IncidentAttachment> savedAttachments =
                new ArrayList<>();

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                savedAttachments.add(
                        uploadAttachment(
                                incidentId,
                                file,
                                uploadedBy
                        )
                );
            }
        }

        if (savedAttachments.isEmpty()) {
            throw new BadRequestException(
                    "At least one non-empty attachment file is required"
            );
        }

        return savedAttachments;
    }

    /**
     * 上传单个事故附件。
     */
    @Transactional
    public IncidentAttachment uploadAttachment(
            Long incidentId,
            MultipartFile file,
            Long uploadedBy
    ) {
        return uploadAttachment(
                incidentId,
                file,
                uploadedBy,
                null
        );
    }

    /**
     * 上传单个事故视频。
     */
    @Transactional
    public IncidentAttachment uploadVideo(
            Long incidentId,
            MultipartFile file,
            Long uploadedBy
    ) {
        return uploadAttachment(
                incidentId,
                file,
                uploadedBy,
                "VIDEO"
        );
    }

    private IncidentAttachment uploadAttachment(
            Long incidentId,
            MultipartFile file,
            Long uploadedBy,
            String attachmentType
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(
                    "Attachment file is required"
            );
        }

        String resolvedAttachmentType =
                resolveAttachmentType(file, attachmentType);

        validateAttachmentFile(file, resolvedAttachmentType);

        Incident incident =
                findIncident(incidentId);

        try {
            Files.createDirectories(uploadDir);

            String extension =
                    resolveExtension(
                            file.getOriginalFilename()
                    );

            String fileName =
                    UUID.randomUUID() + extension;

            Path target = uploadDir
                    .resolve(fileName)
                    .normalize();

            /*
             * 防止保存路径意外逃逸出上传目录。
             */
            if (!target.startsWith(uploadDir)) {
                throw new BadRequestException(
                        "Invalid attachment file path"
                );
            }

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

            IncidentAttachment attachment =
                    new IncidentAttachment();

            attachment.setIncidentId(
                    incident.getId()
            );

            attachment.setFileName(
                    fileName
            );

            attachment.setOriginalFilename(
                    file.getOriginalFilename() == null
                            ? fileName
                            : file.getOriginalFilename()
            );

            attachment.setContentType(
                    file.getContentType()
            );

            attachment.setAttachmentType(
                    resolvedAttachmentType
            );

            attachment.setFileSize(
                    file.getSize()
            );

            attachment.setFilePath(
                    target.toString()
            );

            attachment.setUploadedBy(
                    uploadedBy
            );

            attachment.setRecognitionStatus(
                    "VIDEO".equals(resolvedAttachmentType)
                            ? "NOT_REQUIRED"
                            : "WAITING_DATA_MODULE"
            );

            IncidentAttachment saved =
                    attachmentRepository.save(attachment);

            operationLogService.record(
                    uploadedBy,
                    "UPLOAD_ATTACHMENT",
                    "Incident",
                    incident.getId().toString(),
                    null,
                    saved.getOriginalFilename()
            );

            return saved;
        } catch (IOException ex) {
            throw new BadRequestException(
                    "Failed to save attachment: "
                            + ex.getMessage()
            );
        }
    }

    /**
     * 对上传的照片/视频调用 YOLOv5 进行事故检测，
     * 自动提取事故类型（排除 "car"），保存到附件和事故记录。
     */
    private void runYoloDetection(
            Incident incident,
            List<IncidentAttachment> attachments,
            List<MultipartFile> photos,
            List<MultipartFile> videos,
            MultipartFile singleVideo
    ) {
        java.util.Set<String> allAccidentTypes = new java.util.LinkedHashSet<>();
        List<IncidentAttachment> updatedAttachments = new ArrayList<>();

        for (IncidentAttachment att : attachments) {
            try {
                if ("PHOTO".equals(att.getAttachmentType())) {
                    YoloDetectClient.YoloImageResult result = yoloDetectClient.detectImageByPath(
                            uploadDir.resolve(att.getFileName()).toFile(), yoloDefaultConf);
                    if (result != null && result.success()) {
                        att.setAiDetectedTypes(String.join(",", result.accidentTypes()));
                        att.setAiDetectionJson(result.rawJson());
                        att.setAnnotatedFileUrl(result.outputUrl());
                        att.setRecognitionStatus("COMPLETED");
                        allAccidentTypes.addAll(result.accidentTypes());
                    } else {
                        att.setRecognitionStatus(result != null ? "FAILED" : "PENDING");
                    }
                    updatedAttachments.add(att);

                } else if ("VIDEO".equals(att.getAttachmentType())) {
                    YoloDetectClient.YoloVideoResult result = yoloDetectClient.detectVideoByPath(
                            uploadDir.resolve(att.getFileName()).toFile(), yoloDefaultConf);
                    if (result != null && result.success()) {
                        att.setAiDetectedTypes(String.join(",", result.accidentTypes()));
                        att.setAiDetectionJson(result.rawJson());
                        String browserCompatibleUrl =
                                videoTranscodeService.ensureBrowserCompatible(
                                        result.outputUrl()
                                );
                        att.setAnnotatedFileUrl(browserCompatibleUrl);
                        att.setRecognitionStatus("COMPLETED");
                        allAccidentTypes.addAll(result.accidentTypes());
                    } else {
                        att.setRecognitionStatus(result != null ? "FAILED" : "PENDING");
                    }
                    updatedAttachments.add(att);
                }
            } catch (Exception e) {
                log.warn("附件 {} YOLOv5 检测失败: {}", att.getId(), e.getMessage());
                att.setRecognitionStatus("FAILED");
                updatedAttachments.add(att);
            }
        }

        // 批量保存更新后的附件
        if (!updatedAttachments.isEmpty()) {
            attachmentRepository.saveAll(updatedAttachments);
        }

        // 自动填充事故类型（AI检测结果 → initialAccidentType）
        if (!allAccidentTypes.isEmpty()) {
            String aiType = mergeCsvLabels(incident.getSceneLabels(), allAccidentTypes);
            incident.setSceneLabels(aiType);
            if (incident.getInitialAccidentType() == null || incident.getInitialAccidentType().isBlank()) {
                incident.setInitialAccidentType(aiType);
            }
            incidentRepository.save(incident);
            log.info("事故 {} YOLOv5 AI检测类型: {}", incident.getIncidentNo(), aiType);
        }
    }

    private String normalizeCsvLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return null;
        }
        java.util.Set<String> normalized = java.util.Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(label -> !label.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String mergeCsvLabels(String existingLabels, java.util.Collection<String> newLabels) {
        java.util.Set<String> merged = new java.util.LinkedHashSet<>();
        String normalizedExisting = normalizeCsvLabels(existingLabels);
        if (normalizedExisting != null) {
            merged.addAll(java.util.Arrays.asList(normalizedExisting.split(",")));
        }
        if (newLabels != null) {
            newLabels.stream()
                    .map(label -> label == null ? "" : label.trim())
                    .filter(label -> !label.isBlank())
                    .forEach(merged::add);
        }
        return merged.isEmpty() ? null : String.join(",", merged);
    }

    private void uploadOptionalFiles(
            Long incidentId,
            List<MultipartFile> files,
            Long uploadedBy,
            String attachmentType
    ) {
        if (files == null || files.isEmpty()) {
            return;
        }

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                uploadAttachment(
                        incidentId,
                        file,
                        uploadedBy,
                        attachmentType
                );
            }
        }
    }

    private String resolveAttachmentType(
            MultipartFile file,
            String requestedType
    ) {
        if (requestedType != null
                && !requestedType.isBlank()) {
            return requestedType.trim().toUpperCase();
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase();

        if (contentType.startsWith("image/")) {
            return "PHOTO";
        }

        if (contentType.startsWith("video/")) {
            return "VIDEO";
        }

        String filename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase();

        if (filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".png")
                || filename.endsWith(".webp")) {
            return "PHOTO";
        }

        if (filename.endsWith(".mp4")
                || filename.endsWith(".mov")
                || filename.endsWith(".avi")
                || filename.endsWith(".webm")) {
            return "VIDEO";
        }

        return "OTHER";
    }

    private void validateAttachmentFile(
            MultipartFile file,
            String attachmentType
    ) {
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase();

        if ("PHOTO".equals(attachmentType)
                && !contentType.isBlank()
                && !contentType.startsWith("image/")) {
            throw new BadRequestException(
                    "Photo attachment must be an image file"
            );
        }

        if ("VIDEO".equals(attachmentType)
                && !contentType.isBlank()
                && !contentType.startsWith("video/")) {
            throw new BadRequestException(
                    "Video attachment must be a video file"
            );
        }
    }

    /**
     * 将事故和附件信息提交给数据预测模块。
     */
    @Transactional
    public PredictionSubmitResponse submitPredictionRequest(
            Long incidentId,
            Long operatorUserId
    ) {
        Incident incident =
                findIncident(incidentId);

        List<IncidentAttachment> attachments =
                attachmentRepository
                        .findByIncidentIdOrderByCreatedAtAsc(
                                incidentId
                        );

        PredictionRequest request =
                buildPredictionRequest(
                        incident,
                        attachments
                );

        if (predictionModelClient.isConfigured()) {
            PredictionModuleRequest moduleRequest =
                    buildPredictionModuleRequest(
                            incident,
                            attachments
                    );

            PredictionModuleResponse moduleResponse =
                    predictionModelClient.predict(moduleRequest);

            if (moduleResponse.isCompleted()) {
                PredictionModuleResultRequest merged =
                        mergePredictionModuleResponse(
                                moduleResponse,
                                moduleRequest
                        );

                acceptPredictionResult(
                        incidentId,
                        merged,
                        operatorUserId
                );

                operationLogService.record(
                        operatorUserId,
                        "SUBMIT_PREDICTION_REQUEST_SYNC",
                        "Incident",
                        incident.getId().toString(),
                        null,
                        moduleResponse.traceId()
                );

                return new PredictionSubmitResponse(
                        true,
                        "COMPLETED",
                        "算法2预测已完成并写入事故详情。",
                        moduleResponse.traceId(),
                        request,
                        null
                );
            }

            log.warn(
                    "算法2预测未完成: incidentId={}, status={}, error={}",
                    incidentId,
                    moduleResponse.status(),
                    moduleResponse.errorMessage()
            );

            return new PredictionSubmitResponse(
                    false,
                    moduleResponse.status(),
                    moduleResponse.errorMessage(),
                    moduleResponse.traceId(),
                    request,
                    null
            );
        }

        PredictionSubmitResponse response =
                predictionClient.predict(request);

        if (Boolean.TRUE.equals(response.submitted())) {
            incident.setStatus(
                    IncidentStatus.PREDICTION_REQUESTED
            );

            incidentRepository.save(incident);

            operationLogService.record(
                    operatorUserId,
                    "SUBMIT_PREDICTION_REQUEST",
                    "Incident",
                    incident.getId().toString(),
                    null,
                    response.dataModuleTraceId()
            );

            realtimeService.publish(
                    "INCIDENT_PREDICTION_REQUESTED",
                    Map.of(
                            "incidentId",
                            incident.getId(),

                            "incidentNo",
                            incident.getIncidentNo(),

                            "dataModuleTraceId",
                            response.dataModuleTraceId() == null
                                    ? ""
                                    : response.dataModuleTraceId()
                    )
            );
        }

        return response;
    }

    /**
     * 接收数据预测模块返回的预测结果。
     */
    @Transactional
    public PredictionResult acceptPredictionResult(
            Long incidentId,
            PredictionModuleResultRequest request,
            Long operatorUserId
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Prediction module result is required"
            );
        }

        validatePredictionResult(request);

        Incident incident =
                findIncident(incidentId);

        List<IncidentAttachment> attachments =
                attachmentRepository
                        .findByIncidentIdOrderByCreatedAtAsc(
                                incidentId
                        );

        PredictionModuleResultRequest enhancedRequest =
                applyRecoveryRecommendation(
                        incident,
                        attachments,
                        request
                );

        String riskFactors =
                joinRiskFactors(
                        enhancedRequest.riskFactors()
                );

        String suggestions =
                formalDispositionPlanService.buildFormalPlan(
                        incident,
                        enhancedRequest,
                        riskFactors
                );

        PredictionOutcome outcome =
                new PredictionOutcome(
                        enhancedRequest.accidentType(),
                        enhancedRequest.riskLevel(),
                        enhancedRequest.congestionDurationMinutes(),
                        enhancedRequest.recoveryDurationMinutes(),
                        enhancedRequest.confidence(),
                        firstNonBlank(
                                enhancedRequest.modelVersion(),
                                "data-module"
                        ),
                        suggestions,
                        enhancedRequest.riskFactors(),
                        enhancedRequest.evidenceSummary()
                );

        String explanation =
                firstNonBlank(
                        enhancedRequest.explanation(),
                        deepSeekClient.explain(
                                outcome,
                                incident.getLocationName(),
                                incident.getDescription()
                        )
                );

        PredictionResult result =
                new PredictionResult();

        result.setIncidentId(
                incident.getId()
        );

        result.setAccidentType(
                enhancedRequest.accidentType().trim()
        );

        result.setRiskLevel(
                enhancedRequest.riskLevel()
        );

        result.setRiskScore(
                enhancedRequest.riskScore()
        );

        result.setImageEvidence(
                joinImageEvidence(
                        enhancedRequest.imageEvidence()
                )
        );

        result.setCongestionDurationMinutes(
                enhancedRequest.congestionDurationMinutes()
        );

        result.setRecoveryDurationMinutes(
                enhancedRequest.recoveryDurationMinutes()
        );

        result.setConfidence(
                enhancedRequest.confidence()
        );

        result.setModelVersion(
                outcome.modelVersion()
        );

        result.setSuggestions(
                suggestions
        );

        result.setExplanation(
                explanation
        );

        result.setRiskFactors(
                riskFactors
        );

        result.setEvidenceSummary(
                trimToNull(enhancedRequest.evidenceSummary())
        );

        result.setDataModuleTraceId(
                trimToNull(enhancedRequest.dataModuleTraceId())
        );

        result.setRawResult(
                enhancedRequest.rawResult()
        );

        result.setRecoveryRecommendation(
                trimToNull(enhancedRequest.recoveryRecommendation())
        );

        result.setRecoveryConfidence(
                enhancedRequest.recoveryConfidence()
        );

        result.setRecoveryLevel(
                trimToNull(enhancedRequest.recoveryLevel())
        );

        result.setRecoveryModelVersion(
                trimToNull(enhancedRequest.recoveryModelVersion())
        );

        result.setRecoveryTraceId(
                trimToNull(enhancedRequest.recoveryTraceId())
        );

        result.setRecoveryKeyFactors(
                joinRiskFactors(
                        enhancedRequest.recoveryKeyFactors()
                )
        );

        PredictionResult saved =
                predictionResultRepository.save(result);

        // Algorithm4: RL调度推荐
        try {
            if (dispatchRecommendationClient != null
                    && dispatchRecommendationClient.isConfigured()) {
                var dispatchResult = dispatchRecommendationClient
                        .recommend(incident, attachments, enhancedRequest);
                if (dispatchResult.isPresent()) {
                    var dispatch = dispatchResult.get();
                    String json = toDispatchPlanJson(dispatch);
                    log.info("Algorithm4 dispatch: incidentId={}, plan={}, model={}",
                            incident.getId(),
                            json != null ? json.substring(0, Math.min(80, json.length())) : "null",
                            dispatch.modelVersion());
                    saved.setDispatchPlan(json);
                    saved.setDispatchModelVersion(dispatch.modelVersion());
                    saved.setDispatchTraceId(dispatch.traceId());
                    saved.setModelVersion(joinVersions(saved.getModelVersion(), dispatch.modelVersion()));
                    predictionResultRepository.save(saved);
                } else {
                    log.info("Algorithm4 dispatch: incidentId={}, no recommendation", incident.getId());
                }
            }
        } catch (Exception ex) {
            log.warn("Algorithm4 dispatch failed: incidentId={}, error={}",
                    incident.getId(), ex.getMessage());
        }

        updateIncidentFromPrediction(
                incident,
                saved
        );

        operationLogService.record(
                operatorUserId,
                "ACCEPT_PREDICTION_RESULT",
                "Incident",
                incident.getId().toString(),
                null,
                saved.getRiskLevel().name()
        );

        realtimeService.publish(
                "INCIDENT_PREDICTED",
                Map.of(
                        "incidentId",
                        incident.getId(),

                        "incidentNo",
                        incident.getIncidentNo(),

                        "accidentType",
                        saved.getAccidentType(),

                        "riskLevel",
                        saved.getRiskLevel(),

                        "congestionDurationMinutes",
                        saved.getCongestionDurationMinutes(),

                        "recoveryDurationMinutes",
                        saved.getRecoveryDurationMinutes(),

                        "supportRequired",
                        Boolean.TRUE.equals(
                                incident.getSupportRequired()
                        )
                )
        );

        return saved;
    }

    /**
     * 查询事故附件。
     */
    @Transactional(readOnly = true)
    public IncidentAttachment findAttachment(
            Long incidentId,
            Long attachmentId
    ) {
        Incident incident =
                findIncident(incidentId);

        if (attachmentId == null) {
            throw new BadRequestException(
                    "attachmentId is required"
            );
        }

        IncidentAttachment attachment =
                attachmentRepository
                        .findById(attachmentId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Attachment not found: "
                                                + attachmentId
                                )
                        );

        if (!incident.getId().equals(
                attachment.getIncidentId()
        )) {
            throw new ResourceNotFoundException(
                    "Attachment not found for incident: "
                            + incidentId
            );
        }

        return attachment;
    }

    /**
     * 加载附件文件。
     */
    @Transactional(readOnly = true)
    public Resource loadAttachmentFile(
            IncidentAttachment attachment
    ) {
        if (attachment == null
                || attachment.getFilePath() == null
                || attachment.getFilePath().isBlank()) {
            throw new ResourceNotFoundException(
                    "Attachment file path is empty"
            );
        }

        try {
            Path path = Path.of(
                            attachment.getFilePath()
                    )
                    .toAbsolutePath()
                    .normalize();

            Resource resource =
                    new UrlResource(path.toUri());

            if (!resource.exists()
                    || !resource.isReadable()) {
                throw new ResourceNotFoundException(
                        "Attachment file not found: "
                                + attachment.getId()
                );
            }

            return resource;
        } catch (MalformedURLException ex) {
            throw new BadRequestException(
                    "Invalid attachment file path"
            );
        }
    }

    /**
 * 基于事故最新预测结果，重新生成自然语言解释。
 */
@Transactional
public PredictionDisplayResponse regenerateLatestPredictionExplanation(
        Long incidentId,
        Long operatorUserId
) {
    Incident incident = findIncident(incidentId);

    PredictionResult result =
            predictionResultRepository
                    .findFirstByIncidentIdOrderByCreatedAtDesc(
                            incidentId
                    )
                    .orElseThrow(
                            () -> new ResourceNotFoundException(
                                    "Prediction result not found for incident: "
                                            + incidentId
                            )
                    );

    List<String> riskFactors =
            result.getRiskFactors() == null
                    || result.getRiskFactors().isBlank()
                    ? List.of()
                    : List.of(result.getRiskFactors());

    PredictionOutcome outcome =
            new PredictionOutcome(
                    result.getAccidentType(),
                    result.getRiskLevel(),
                    result.getCongestionDurationMinutes(),
                    result.getRecoveryDurationMinutes(),
                    result.getConfidence(),
                    result.getModelVersion(),
                    result.getSuggestions(),
                    riskFactors,
                    result.getEvidenceSummary()
            );

    String explanation =
            deepSeekClient.explain(
                    outcome,
                    incident.getLocationName(),
                    incident.getDescription()
            );

    if (explanation == null || explanation.isBlank()) {
        throw new ExternalServiceException(
                "Failed to generate prediction explanation"
        );
    }

    explanation = explanation.trim();

    // prediction_results.explanation 字段最大长度为 1500
    if (explanation.length() > 1500) {
        explanation = explanation.substring(0, 1500);
    }

    result.setExplanation(explanation);

    PredictionResult saved =
            predictionResultRepository.save(result);

    operationLogService.record(
            operatorUserId,
            "REGENERATE_PREDICTION_EXPLANATION",
            "PredictionResult",
            saved.getId().toString(),
            null,
            "incidentId=" + incidentId
    );

    realtimeService.publish(
            "PREDICTION_EXPLANATION_REGENERATED",
            Map.of(
                    "incidentId",
                    incidentId,

                    "predictionResultId",
                    saved.getId()
            )
    );

    return PredictionDisplayResponse.from(
            incident,
            saved
    );
}

    /**
     * 查询最新预测结果。
     */
    @Transactional(readOnly = true)
    public PredictionDisplayResponse findLatestPrediction(
            Long incidentId
    ) {
        Incident incident =
                findIncident(incidentId);

        PredictionResult result =
                predictionResultRepository
                        .findFirstByIncidentIdOrderByCreatedAtDesc(
                                incidentId
                        )
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Prediction result not found for incident: "
                                                + incidentId
                                )
                        );

        return PredictionDisplayResponse.from(
                incident,
                result
        );
    }

    /**
     * 市民匿名查询预测状态（通过 trackingToken 验证）。
     *
     * @return 处理中返回 {status:"PROCESSING", completed:false}，
     *         完成后返回 {status:"COMPLETED", completed:true, result:...}
     */
    @Transactional(readOnly = true)
    public PredictionStatusResponse findPredictionStatus(
            Long incidentId,
            String trackingToken
    ) {
        Incident incident = findIncident(incidentId);

        if (incident.getTrackingToken() == null
                || !incident.getTrackingToken().equals(trackingToken)) {
            throw new ResourceNotFoundException(
                    "Incident not found or invalid tracking token"
            );
        }

        PredictionResult result = predictionResultRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElse(null);

        if (result == null) {
            return new PredictionStatusResponse(
                    "PROCESSING",
                    false,
                    null,
                    "事故预测正在处理中，预计约1分钟完成"
            );
        }

        return new PredictionStatusResponse(
                "COMPLETED",
                true,
                PredictionDisplayResponse.from(incident, result),
                "事故预测已完成"
        );
    }

    /**
     * 分页查询事故。
     */
    @Transactional(readOnly = true)
    public Page<Incident> findAll(
            IncidentStatus status,
            RiskLevel riskLevel,
            String keyword,
            Pageable pageable
    ) {
        return incidentRepository.findAll(
                buildSpecification(
                        status,
                        riskLevel,
                        keyword
                ),
                pageable
        );
    }

    /**
     * 查询事故详情。
     */
    @Transactional(readOnly = true)
    public IncidentDetailResponse findDetail(
            Long incidentId
    ) {
        Incident incident =
                findIncident(incidentId);

        return new IncidentDetailResponse(
                incident,

                attachmentRepository
                        .findByIncidentIdOrderByCreatedAtAsc(
                                incidentId
                        ),

                predictionResultRepository
                        .findByIncidentIdOrderByCreatedAtDesc(
                                incidentId
                        ),

                dispatchTaskRepository
                        .findByIncidentIdOrderByCreatedAtDesc(
                                incidentId
                        )
        );
    }

    /**
     * 更新事故状态。
     */
    @Transactional
    public Incident updateStatus(
            Long incidentId,
            IncidentStatus status
    ) {
        if (status == null) {
            throw new BadRequestException(
                    "status is required"
            );
        }

        Incident incident =
                findIncident(incidentId);

        incident.setStatus(status);

        return incidentRepository.save(incident);
    }

    /**
     * 根据ID查询事故。
     */
    @Transactional(readOnly = true)
    public Incident findIncident(
            Long incidentId
    ) {
        if (incidentId == null) {
            throw new BadRequestException(
                    "incidentId is required"
            );
        }

        return incidentRepository
                .findById(incidentId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Incident not found: "
                                        + incidentId
                        )
                );
    }

    /**
     * 构造发送给数据预测模块的请求。
     */
    private PredictionRequest buildPredictionRequest(
            Incident incident,
            List<IncidentAttachment> attachments
    ) {
        List<PredictionAttachmentPayload> attachmentPayloads =
                attachments == null
                        ? List.of()
                        : attachments.stream()
                        .map(this::toPredictionAttachmentPayload)
                        .toList();

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
                attachmentPayloads
        );
    }

    /**
     * 构造发送给算法2同步预测模块的请求。
     */
    private PredictionModuleRequest buildPredictionModuleRequest(
            Incident incident,
            List<IncidentAttachment> attachments
    ) {
        List<PredictionAttachmentPayload> attachmentPayloads =
                attachments == null
                        ? List.of()
                        : attachments.stream()
                        .map(this::toPredictionAttachmentPayload)
                        .toList();

        return new PredictionModuleRequest(
                incident.getId(),
                incident.getIncidentNo(),
                List.of(
                        PredictionAlgorithmType.ACCIDENT_TYPE,
                        PredictionAlgorithmType.RISK_IMPACT
                ),
                incident.getLocationName(),
                incident.getAddress(),
                incident.getDescription(),
                incident.getLongitude(),
                incident.getLatitude(),
                incident.getOccupiedLanes(),
                incident.getTrafficFlow(),
                incident.getPeopleFlow(),
                incident.getWeather(),
                incident.getRoadLevel(),
                incident.getRoadName(),
                attachmentPayloads
        );
    }

    /**
     * 将算法2同步响应合并为后端现有的预测结果入库 DTO。
     */
    private PredictionModuleResultRequest mergePredictionModuleResponse(
            PredictionModuleResponse response,
            PredictionModuleRequest request
    ) {
        PredictionModuleResponse.PredictionModuleResponseResults results =
                response.results();

        AccidentTypeResult accidentTypeResult =
                results == null ? null : results.accidentType();
        RiskImpactResult riskImpactResult =
                results == null ? null : results.riskImpact();

        String accidentType =
                accidentTypeResult == null
                        ? firstNonBlank(
                        request.description(),
                        "其他交通事故"
                )
                        : firstNonBlank(
                        accidentTypeResult.accidentType(),
                        request.description(),
                        "其他交通事故"
                );

        RiskLevel riskLevel =
                riskImpactResult == null
                        || riskImpactResult.riskLevel() == null
                        ? RiskLevel.MEDIUM
                        : normalizeThreeClassRisk(
                        riskImpactResult.riskLevel()
                );

        Double confidence =
                max(
                        accidentTypeResult == null
                                ? null
                                : accidentTypeResult.confidence(),
                        riskImpactResult == null
                                ? null
                                : riskImpactResult.confidence()
                );

        if (confidence == null) {
            confidence = 0.75;
        }

        Integer congestionDurationMinutes =
                riskImpactResult == null
                        || riskImpactResult.congestionDurationMinutes() == null
                        ? 20
                        : riskImpactResult.congestionDurationMinutes();

        Integer recoveryDurationMinutes =
                riskImpactResult == null
                        || riskImpactResult.recoveryDurationMinutes() == null
                        ? Math.max(40, congestionDurationMinutes + 10)
                        : riskImpactResult.recoveryDurationMinutes();

        if (recoveryDurationMinutes < congestionDurationMinutes) {
            recoveryDurationMinutes = congestionDurationMinutes + 10;
        }

        return new PredictionModuleResultRequest(
                accidentType,
                riskLevel,
                riskImpactResult == null
                        ? null
                        : riskImpactResult.riskScore(),
                congestionDurationMinutes,
                recoveryDurationMinutes,
                confidence,
                joinVersions(
                        accidentTypeResult == null
                                ? null
                                : accidentTypeResult.modelVersion(),
                        riskImpactResult == null
                                ? null
                                : riskImpactResult.modelVersion()
                ),
                riskImpactResult == null
                        ? null
                        : riskImpactResult.riskFactors(),
                accidentTypeResult == null
                        ? null
                        : accidentTypeResult.imageEvidence(),
                accidentTypeResult == null
                        ? null
                        : accidentTypeResult.evidenceSummary(),
                response.traceId(),
                null,
                riskImpactResult == null
                        ? null
                        : riskImpactResult.suggestion(),
                riskImpactResult == null
                        ? null
                        : riskImpactResult.explanation(),
                null, null, null, null, null, null,
                null, null, null
        );
    }

    private RiskLevel normalizeThreeClassRisk(
            RiskLevel riskLevel
    ) {
        if (riskLevel == RiskLevel.CRITICAL) {
            return RiskLevel.HIGH;
        }

        return riskLevel;
    }

    private static Double max(Double a, Double b) {
        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        return a >= b ? a : b;
    }

    private static String joinVersions(String a, String b) {
        if (a == null && b == null) {
            return "algorithm2-expert-fusion";
        }

        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        return a + "+" + b;
    }

    private PredictionAttachmentPayload toPredictionAttachmentPayload(
            IncidentAttachment attachment
    ) {
        return new PredictionAttachmentPayload(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getFilePath()
        );
    }

    /**
     * 用预测结果更新事故主表。
     */
    private void updateIncidentFromPrediction(
            Incident incident,
            PredictionResult result
    ) {
        incident.setConfirmedAccidentType(
                result.getAccidentType()
        );

        incident.setRiskLevel(
                result.getRiskLevel()
        );

        incident.setPredictedCongestionMinutes(
                result.getCongestionDurationMinutes()
        );

        incident.setPredictedRecoveryMinutes(
                result.getRecoveryDurationMinutes()
        );

        incident.setConfidence(
                result.getConfidence()
        );

        incident.setSuggestion(
                result.getSuggestions()
        );

        incident.setExplanation(
                result.getExplanation()
        );

        incident.setStatus(
                IncidentStatus.PREDICTED
        );

        /*
         * 预测完成后，重新进行自动支援判断并刷新预计到达时间。
         */
        applyAutomaticSupportDecision(incident);
        arrivalEstimateService.applyEstimate(incident);

        incidentRepository.save(incident);
    }

    /**
     * 构造事故分页查询条件。
     */
    private Specification<Incident> buildSpecification(
            IncidentStatus status,
            RiskLevel riskLevel,
            String keyword
    ) {
        return (
                root,
                query,
                criteriaBuilder
        ) -> {
            List<Predicate> predicates =
                    new ArrayList<>();

            if (status != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("status"),
                                status
                        )
                );
            }

            if (riskLevel != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("riskLevel"),
                                riskLevel
                        )
                );
            }

            if (keyword != null
                    && !keyword.isBlank()) {
                String like =
                        "%"
                                + keyword
                                .trim()
                                .toLowerCase()
                                + "%";

                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get(
                                                        "incidentNo"
                                                )
                                        ),
                                        like
                                ),

                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get(
                                                        "locationName"
                                                )
                                        ),
                                        like
                                ),

                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get(
                                                        "description"
                                                )
                                        ),
                                        like
                                )
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(
                            new Predicate[0]
                    )
            );
        };
    }

    /**
     * 根据事故数据自动判断是否需要支援。
     */
    private void applyAutomaticSupportDecision(
            Incident incident
    ) {
        /*
         * 指挥中心已经人工判断时，不再自动覆盖。
         */
        if (Boolean.TRUE.equals(
                incident.getSupportDecisionManual()
        )) {
            return;
        }

        String reason =
                calculateSupportReason(incident);

        incident.setSupportRequired(
                reason != null
        );

        incident.setSupportReason(
                reason == null
                        ? "当前未触发自动支援条件"
                        : reason
        );
    }

    private String calculateSupportReason(
            Incident incident
    ) {
        if (incident.getRiskLevel()
                == RiskLevel.CRITICAL) {
            return "风险等级为严重，"
                    + "建议立即调度交警、救援、医疗和清障资源";
        }

        if (incident.getRiskLevel()
                == RiskLevel.HIGH) {
            return "风险等级为高，"
                    + "建议调度交警和清障/救援资源";
        }

        if (incident.getPredictedCongestionMinutes()
                != null
                && incident.getPredictedCongestionMinutes()
                >= 60) {
            return "预计拥堵持续时间达到60分钟及以上，"
                    + "建议安排现场支援";
        }

        if (incident.getOccupiedLanes() != null
                && incident.getOccupiedLanes() >= 2) {
            return "事故占用两条及以上车道，"
                    + "建议安排交通疏导和清障支援";
        }

        String text =
                (
                        firstNonBlank(
                                incident.getDescription(),
                                ""
                        )
                                + " "
                                + firstNonBlank(
                                incident.getConfirmedAccidentType(),
                                incident.getInitialAccidentType(),
                                ""
                        )
                ).toLowerCase();

        if (containsAny(
                text,
                "受伤",
                "伤亡",
                "起火",
                "爆炸",
                "侧翻",
                "泄漏",
                "被困",
                "多车"
        )) {
            return "事故描述包含人员伤亡或重大危险特征，"
                    + "建议安排应急支援";
        }

        return null;
    }

    private boolean containsAny(
            String text,
            String... words
    ) {
        if (text == null) {
            return false;
        }

        for (String word : words) {
            if (word != null
                    && text.contains(
                    word.toLowerCase()
            )) {
                return true;
            }
        }

        return false;
    }

    private String generateIncidentNo() {
        return "ACC"
                + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMddHHmmss"
                )
        )
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6);
    }

    private String resolveExtension(
            String originalFilename
    ) {
        if (originalFilename == null
                || !originalFilename.contains(".")) {
            return "";
        }

        String extension =
                originalFilename.substring(
                        originalFilename.lastIndexOf('.')
                );

        return extension.length() > 10
                ? ""
                : extension;
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private String trimToNull(
            String value
    ) {
        return value == null
                || value.trim().isEmpty()
                ? null
                : value.trim();
    }

    /**
     * 校验数据模块返回的预测结果。
     */
    private PredictionModuleResultRequest applyRecoveryRecommendation(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest request
    ) {
        if (recoveryRecommendationClient == null
                || !recoveryRecommendationClient.isConfigured()) {
            return request;
        }

        return recoveryRecommendationClient
                .recommend(
                        incident,
                        attachments,
                        request
                )
                .map(recovery -> mergeRecoveryRecommendation(
                        request,
                        recovery
                ))
                .orElse(request);
    }

    private PredictionModuleResultRequest mergeRecoveryRecommendation(
            PredictionModuleResultRequest request,
            RecoveryRecommendationResult recovery
    ) {
        Integer predictedRecovery =
                recovery.predictedRecoveryDurationMinutes();

        Integer recoveryDurationMinutes =
                predictedRecovery == null
                        ? request.recoveryDurationMinutes()
                        : Math.max(
                        predictedRecovery,
                        request.congestionDurationMinutes() + 5
                );

        List<String> riskFactors =
                mergeFactors(
                        request.riskFactors(),
                        recovery.keyFactors()
                );

        return new PredictionModuleResultRequest(
                request.accidentType(),
                request.riskLevel(),
                request.riskScore(),
                request.congestionDurationMinutes(),
                recoveryDurationMinutes,
                request.confidence(),
                joinVersions(
                        request.modelVersion(),
                        recovery.modelVersion()
                ),
                riskFactors,
                request.imageEvidence(),
                request.evidenceSummary(),
                request.dataModuleTraceId(),
                request.rawResult(),
                request.suggestion(),
                request.explanation(),
                recovery.recommendation(),
                recovery.confidence(),
                recovery.recoveryLevel(),
                recovery.modelVersion(),
                recovery.traceId(),
                recovery.keyFactors(),
                request.dispatchPlan(),
                request.dispatchModelVersion(),
                request.dispatchTraceId()
        );
    }

    private List<String> mergeFactors(
            List<String> primary,
            List<String> secondary
    ) {
        List<String> merged =
                new ArrayList<>();

        addFactors(merged, primary);
        addFactors(merged, secondary);

        return merged.isEmpty()
                ? null
                : merged;
    }

    private void addFactors(
            List<String> target,
            List<String> values
    ) {
        if (values == null) {
            return;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()
                    && !target.contains(value.trim())) {
                target.add(value.trim());
            }
        }
    }

    // ──────────── Algorithm4 Dispatch Recommendation ────────────

    private PredictionModuleResultRequest applyDispatchRecommendation(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest request
    ) {
        if (dispatchRecommendationClient == null
                || !dispatchRecommendationClient.isConfigured()) {
            return request;
        }

        return dispatchRecommendationClient
                .recommend(incident, attachments, request)
                .map(dispatch -> {
                    // Store dispatch plan as JSON for later saving
                    String json = toDispatchPlanJson(dispatch);
                    return new PredictionModuleResultRequest(
                            request.accidentType(), request.riskLevel(),
                            request.riskScore(), request.congestionDurationMinutes(),
                            request.recoveryDurationMinutes(), request.confidence(),
                            joinVersions(request.modelVersion(), dispatch.modelVersion()),
                            request.riskFactors(), request.imageEvidence(),
                            request.evidenceSummary(), request.dataModuleTraceId(),
                            request.rawResult(), request.suggestion(), request.explanation(),
                            request.recoveryRecommendation(), request.recoveryConfidence(),
                            request.recoveryLevel(), request.recoveryModelVersion(),
                            request.recoveryTraceId(), request.recoveryKeyFactors(),
                            json, dispatch.modelVersion(), dispatch.traceId()
                    );
                })
                .orElse(request);
    }

    private String toDispatchPlanJson(DispatchRecommendationResult dispatch) {
        if (dispatch.dispatchPlan() == null || dispatch.dispatchPlan().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dispatch.dispatchPlan().size(); i++) {
            var item = dispatch.dispatchPlan().get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"taskType\":\"%s\",\"priority\":\"%s\",\"units\":%d,\"etaMin\":%d,\"reasoning\":\"%s\",\"score\":%.4f}",
                item.taskType(), item.priority(), item.recommendedUnits(),
                item.estimatedArrivalMinutes(),
                item.reasoning().replace("\"", "'"), item.score()
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    private void validatePredictionResult(
            PredictionModuleResultRequest request
    ) {
        if (request.accidentType() == null
                || request.accidentType().isBlank()) {
            throw new BadRequestException(
                    "accidentType is required"
            );
        }

        if (request.riskLevel() == null) {
            throw new BadRequestException(
                    "riskLevel is required"
            );
        }

        if (request.congestionDurationMinutes() == null
                || request.congestionDurationMinutes() < 0) {
            throw new BadRequestException(
                    "congestionDurationMinutes must be greater than or equal to 0"
            );
        }

        if (request.recoveryDurationMinutes() == null
                || request.recoveryDurationMinutes() < 0) {
            throw new BadRequestException(
                    "recoveryDurationMinutes must be greater than or equal to 0"
            );
        }

        if (request.recoveryDurationMinutes()
                < request.congestionDurationMinutes()) {
            throw new BadRequestException(
                    "recoveryDurationMinutes should not be less than congestionDurationMinutes"
            );
        }

        if (request.confidence() == null
                || request.confidence() < 0
                || request.confidence() > 1) {
            throw new BadRequestException(
                    "confidence must be between 0 and 1"
            );
        }
    }

    /**
     * 根据预测结果生成调度建议。
     */
    private String buildDispositionSuggestion(
            Incident incident,
            PredictionModuleResultRequest request,
            String riskFactors
    ) {
        StringJoiner joiner =
                new StringJoiner(" ");

        joiner.add(
                switch (request.riskLevel()) {
                    case LOW ->
                            "低风险：现场交警可设置警示标志，引导车辆减速通过，持续观察现场变化。";

                    case MEDIUM ->
                            "中风险：建议保护事故现场，视情况实施局部车道管控，并通知指挥中心关注拥堵变化。";

                    case HIGH ->
                            "高风险：建议立即通知指挥中心，安排警力到场，优先疏导受影响车道，并联系清障车辆。";

                    case CRITICAL ->
                            "严重风险：建议启动应急处置流程，联动交警、清障、救援和医疗资源，扩大交通管制范围。";
                }
        );

        String accidentType =
                request.accidentType().toLowerCase();

        if (accidentType.contains("封闭")
                || accidentType.contains("block")) {
            joiner.add(
                    "事故涉及道路封闭或车道阻断，应优先设置分流路线。"
            );
        }

        if (incident.getOccupiedLanes() != null
                && incident.getOccupiedLanes() >= 2) {
            joiner.add(
                    "占用车道较多，建议提前发布绕行提示。"
            );
        }

        if (riskFactors != null
                && !riskFactors.isBlank()) {
            joiner.add(
                    "主要风险因子："
                            + riskFactors
                            + "。"
            );
        }

        return joiner.toString();
    }

    private String joinImageEvidence(
            List<String> imageEvidence
    ) {
        if (imageEvidence == null
                || imageEvidence.isEmpty()) {
            return null;
        }

        StringJoiner joiner =
                new StringJoiner("；");

        for (String evidence : imageEvidence) {
            if (evidence != null
                    && !evidence.isBlank()) {
                joiner.add(evidence.trim());
            }
        }

        String result = joiner.toString();

        return result.isBlank()
                ? null
                : result;
    }

    private String joinRiskFactors(
            List<String> riskFactors
    ) {
        if (riskFactors == null
                || riskFactors.isEmpty()) {
            return null;
        }

        StringJoiner joiner =
                new StringJoiner("、");

        for (String factor : riskFactors) {
            if (factor != null
                    && !factor.isBlank()) {
                joiner.add(
                        factor.trim()
                );
            }
        }

        String result =
                joiner.toString();

        return result.isBlank()
                ? null
                : result;
    }

    /**
     * 供事故提交后的后台监听器调用，对已保存的附件执行 YOLOv5 检测。
     * 复用已有的 {@link #runYoloDetection} 私有方法。
     */
    @Transactional
    public void runYoloDetectionByIncidentId(Long incidentId) {
        Incident incident = findIncident(incidentId);
        List<IncidentAttachment> attachments =
                attachmentRepository.findByIncidentId(incidentId);

        if (attachments == null || attachments.isEmpty()) {
            log.info(
                    "事故 {} 没有可识别的附件，跳过 YOLO 检测",
                    incident.getIncidentNo()
            );
            return;
        }

        runYoloDetection(
                incident,
                attachments,
                null,
                null,
                null
        );
    }
}
