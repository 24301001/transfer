package com.transfer.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.ClearanceRescueAdviceResponse;
import com.transfer.dto.CommandDispatchRequest;
import com.transfer.dto.CommandIncidentDetailResponse;
import com.transfer.dto.CommandIncidentSummaryResponse;
import com.transfer.dto.ConfirmClearanceRescueAdviceRequest;
import com.transfer.dto.IncidentMapMarkerResponse;
import com.transfer.dto.MapLocationResponse;
import com.transfer.dto.PredictionDisplayResponse;
import com.transfer.dto.ResponderResponse;
import com.transfer.dto.SupportDecisionRequest;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.IncidentAttachmentRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.PredictionResultRepository;
import com.transfer.repository.UserAccountRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class CommandCenterService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CommandCenterService.class
            );

    private static final List<TaskStatus>
            ACTIVE_TASK_STATUSES = List.of(
            TaskStatus.DISPATCHED,
            TaskStatus.DEPARTED,
            TaskStatus.ARRIVED,
            TaskStatus.PROCESSING
    );

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final UserAccountRepository userAccountRepository;

    private final IncidentService incidentService;
    private final DispatchTaskService dispatchTaskService;
    private final MapService mapService;

    private final ClearanceRescueAdviceService
            clearanceRescueAdviceService;

    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    private final String yoloBaseUrl;
    private final int yoloConnectTimeout;
    private final int yoloReadTimeout;

    public CommandCenterService(
            IncidentRepository incidentRepository,
            IncidentAttachmentRepository attachmentRepository,
            PredictionResultRepository predictionResultRepository,
            DispatchTaskRepository dispatchTaskRepository,
            UserAccountRepository userAccountRepository,
            IncidentService incidentService,
            DispatchTaskService dispatchTaskService,
            MapService mapService,
            ClearanceRescueAdviceService clearanceRescueAdviceService,
            OperationLogService operationLogService,
            RealtimeService realtimeService,
            @Value("${app.yolo.base-url:http://localhost:8000}")
            String yoloBaseUrl,
            @Value("${app.yolo.connect-timeout:3000}")
            int yoloConnectTimeout,
            @Value("${app.yolo.read-timeout:30000}")
            int yoloReadTimeout
    ) {
        this.incidentRepository = incidentRepository;
        this.attachmentRepository =
                attachmentRepository;
        this.predictionResultRepository =
                predictionResultRepository;
        this.dispatchTaskRepository =
                dispatchTaskRepository;
        this.userAccountRepository =
                userAccountRepository;
        this.incidentService = incidentService;
        this.dispatchTaskService =
                dispatchTaskService;
        this.mapService = mapService;
        this.clearanceRescueAdviceService =
                clearanceRescueAdviceService;
        this.operationLogService =
                operationLogService;
        this.realtimeService = realtimeService;
        this.yoloBaseUrl = yoloBaseUrl;
        this.yoloConnectTimeout = yoloConnectTimeout;
        this.yoloReadTimeout = yoloReadTimeout;
    }

    /**
     * FR-13～FR-18：查询事故列表。
     */
    @Transactional(readOnly = true)
    public Page<CommandIncidentSummaryResponse>
    findIncidents(
            IncidentStatus status,
            RiskLevel riskLevel,
            Boolean supportRequired,
            String keyword,
            Pageable pageable
    ) {
        Specification<Incident> specification =
                buildSpecification(
                        status,
                        riskLevel,
                        supportRequired,
                        keyword
                );

        return incidentRepository
                .findAll(
                        specification,
                        pageable
                )
                .map(this::toSummary);
    }

    /**
     * 查询事故详情。
     */
    @Transactional
    public CommandIncidentDetailResponse findDetail(
            Long incidentId
    ) {
        Incident incident =
                incidentService.findIncident(
                        incidentId
                );

        MapLocationResponse map =
                ensureMapLocation(incident);

        return new CommandIncidentDetailResponse(
                incident,
                map,

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
     * FR-14：查看事故地图位置。
     */
    @Transactional
    public MapLocationResponse findLocation(
            Long incidentId
    ) {
        Incident incident =
                incidentService.findIncident(
                        incidentId
                );

        return ensureMapLocation(incident);
    }

    /**
     * 获取前端地图需要的所有事故点。
     */
    @Transactional
    public List<IncidentMapMarkerResponse>
    findMapMarkers(
            IncidentStatus status,
            RiskLevel riskLevel,
            Boolean supportRequired,
            String keyword
    ) {
        List<Incident> incidents =
                incidentRepository.findAll(
                        buildSpecification(
                                status,
                                riskLevel,
                                supportRequired,
                                keyword
                        ),

                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        List<IncidentMapMarkerResponse> markers =
                new ArrayList<>();

        for (Incident incident : incidents) {
            MapLocationResponse map =
                    ensureMapLocation(incident);

            markers.add(
                    new IncidentMapMarkerResponse(
                            incident.getId(),
                            incident.getIncidentNo(),
                            incident.getLocationName(),

                            resolveAccidentType(incident),
                            incident.getRiskLevel(),
                            incident.getSupportRequired(),
                            incident.getStatus(),

                            map.baiduLongitude(),
                            map.baiduLatitude(),

                            map.mapReady(),
                            map.message()
                    )
            );
        }

        return markers;
    }

    /**
     * FR-18：人工修改是否需要支援。
     */
    @Transactional
    public Incident updateSupportDecision(
            Long incidentId,
            SupportDecisionRequest request
    ) {
        Incident incident =
                incidentService.findIncident(
                        incidentId
                );

        incident.setSupportRequired(
                request.supportRequired()
        );

        incident.setSupportReason(
                resolveSupportReason(request)
        );

        incident.setSupportDecisionManual(true);

        incident.setSupportDecisionByUserId(
                request.decidedByUserId()
        );

        incident.setSupportDecisionAt(
                LocalDateTime.now()
        );

        Incident saved =
                incidentRepository.save(incident);

        operationLogService.record(
                request.decidedByUserId(),
                "UPDATE_SUPPORT_DECISION",
                "Incident",
                saved.getId().toString(),
                null,
                String.valueOf(
                        saved.getSupportRequired()
                )
        );

        realtimeService.publish(
                "INCIDENT_SUPPORT_DECISION_UPDATED",
                Map.of(
                        "incidentId",
                        saved.getId(),

                        "supportRequired",
                        saved.getSupportRequired()
                )
        );

        return saved;
    }

    /**
     * 指挥中心查看最新预测结果表和自然语言解释。
     */
    @Transactional(readOnly = true)
    public PredictionDisplayResponse findLatestPredictionResult(
            Long incidentId
    ) {
        return incidentService.findLatestPrediction(
                incidentId
        );
    }

    /**
     * 指挥中心手动调用 AI，基于最新预测结果生成自然语言解释。
     */
    @Transactional
    public PredictionDisplayResponse regeneratePredictionExplanation(
            Long incidentId,
            Long operatorUserId
    ) {
        return incidentService
                .regenerateLatestPredictionExplanation(
                        incidentId,
                        operatorUserId
                );
    }

    /**
     * 查询事故最新的清障救援建议。
     *
     * 该能力保留给后续清障救援模块复用，
     * 指挥中心接口不再直接暴露该清障建议流程。
     */
    @Transactional(readOnly = true)
    public Optional<ClearanceRescueAdviceResponse>
    findLatestClearanceRescueAdvice(
            Long incidentId
    ) {
        return clearanceRescueAdviceService
                .findLatest(incidentId);
    }

    /**
     * 指挥人员手动重新生成建议。
     */
    @Transactional
    public ClearanceRescueAdviceResponse
    regenerateClearanceRescueAdvice(
            Long incidentId,
            Long operatorUserId
    ) {
        return clearanceRescueAdviceService
                .generateLatestDraft(
                        incidentId,
                        operatorUserId
                );
    }

    /**
     * 由指挥中心审核修改后确认。
     */
    @Transactional
    public ClearanceRescueAdviceResponse
    confirmClearanceRescueAdvice(
            Long incidentId,
            ConfirmClearanceRescueAdviceRequest request
    ) {
        return clearanceRescueAdviceService
                .confirm(
                        incidentId,
                        request
                );
    }

    /**
     * FR-19：批量调度处理。
     */
    @Transactional
    public List<DispatchTask> dispatch(
            Long incidentId,
            CommandDispatchRequest request
    ) {
        return dispatchTaskService.createBatch(
                incidentId,
                request
        );
    }

    /**
     * 查询可以被调度的现场人员。
     */
    @Transactional(readOnly = true)
    public List<ResponderResponse> findResponders(
            UserRole role
    ) {
        List<UserRole> roles =
                role == null
                        ? List.of(
                                UserRole.FIELD_OFFICER,
                                UserRole.RESCUE_WORKER
                        )
                        : List.of(role);

        return userAccountRepository
                .findByRoleInAndStatusOrderByFullNameAsc(
                        roles,
                        UserStatus.ENABLED
                )
                .stream()
                .map(
                        user -> new ResponderResponse(
                                user.getId(),
                                user.getFullName(),
                                user.getUsername(),
                                user.getPhone(),
                                user.getRole()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DispatchTask>
    findIncidentDispatchTasks(
            Long incidentId
    ) {
        incidentService.findIncident(incidentId);

        return dispatchTaskRepository
                .findByIncidentIdOrderByCreatedAtDesc(
                        incidentId
                );
    }

    private MapLocationResponse ensureMapLocation(
            Incident incident
    ) {
        try {
            MapLocationResponse response =
                    mapService.enrichIncident(
                            incident
                    );

            if (incident.getId() != null) {
                incidentRepository.save(incident);
            }

            return response;

        } catch (ExternalServiceException ex) {
            log.warn(
                    "事故 {} 地图位置处理失败: {}",
                    incident.getIncidentNo(),
                    ex.getMessage()
            );

            return mapService.fromStoredIncident(
                    incident,
                    "百度地图服务暂不可用: "
                            + ex.getMessage()
            );
        }
    }

    private CommandIncidentSummaryResponse toSummary(
            Incident incident
    ) {
        long taskCount =
                dispatchTaskRepository.countByIncidentId(
                        incident.getId()
                );

        long activeTaskCount =
                dispatchTaskRepository
                        .countByIncidentIdAndStatusIn(
                                incident.getId(),
                                ACTIVE_TASK_STATUSES
                        );

        MapLocationResponse map =
                mapService.fromStoredIncident(
                        incident,

                        incident.getBaiduLongitude() == null
                                ? "尚未生成百度地图坐标"
                                : "已生成百度地图坐标"
                );

        return new CommandIncidentSummaryResponse(
                incident.getId(),
                incident.getIncidentNo(),

                resolveAccidentType(incident),
                incident.getRiskLevel(),

                incident.getPredictedCongestionMinutes(),
                incident.getPredictedRecoveryMinutes(),

                incident.getSupportRequired(),
                incident.getSupportReason(),

                incident.getStatus(),

                incident.getLocationName(),
                incident.getAddress(),

                incident.getLongitude(),
                incident.getLatitude(),
                incident.getCoordinateType(),

                incident.getBaiduLongitude(),
                incident.getBaiduLatitude(),

                incident.getMapFormattedAddress(),
                map.navigationUrl(),

                incident.getCreatedAt(),

                taskCount,
                activeTaskCount
        );
    }

    private Specification<Incident>
    buildSpecification(
            IncidentStatus status,
            RiskLevel riskLevel,
            Boolean supportRequired,
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

            if (supportRequired != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("supportRequired"),
                                supportRequired
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
                                                        "address"
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
                                ),

                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get(
                                                        "confirmedAccidentType"
                                                )
                                        ),
                                        like
                                ),

                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get(
                                                        "initialAccidentType"
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

    private String resolveAccidentType(
            Incident incident
    ) {
        return firstNonBlank(
                incident.getConfirmedAccidentType(),
                incident.getInitialAccidentType(),
                "待识别"
        );
    }

    private String resolveSupportReason(
            SupportDecisionRequest request
    ) {
        if (request.reason() != null
                && !request.reason().isBlank()) {
            return request.reason().trim();
        }

        return Boolean.TRUE.equals(
                request.supportRequired()
        )
                ? "指挥中心人工判断需要支援"
                : "指挥中心人工判断暂不需要支援";
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

    /**
     * 获取事故的 AI 检测附件列表（供指挥中心查看带检测框的图片/视频）。
     *
     * <p>是否可展示应以“处理完成且存在输出媒体地址”为准，不能依赖
     * aiDetectedTypes。视频即使没有识别到事故类别，也可能已经正确生成
     * 带检测框的输出文件。</p>
     */
    @Transactional(readOnly = true)
    public List<IncidentAttachment> findAiDetectedAttachments(
            Long incidentId
    ) {
        incidentService.findIncident(incidentId);

        return attachmentRepository
                .findByIncidentIdOrderByCreatedAtAsc(incidentId)
                .stream()
                .filter(attachment -> "COMPLETED".equals(
                        attachment.getRecognitionStatus()
                ))
                .filter(attachment -> attachment.getAnnotatedFileUrl() != null
                        && !attachment.getAnnotatedFileUrl().isBlank())
                .toList();
    }

    /**
     * 打开 YOLO 处理后的媒体流，并透传 Range 相关响应头。
     */
    @Transactional(readOnly = true)
    public AiAttachmentMediaStream openAiAttachmentMedia(
            Long incidentId,
            Long attachmentId,
            String rangeHeader
    ) {
        IncidentAttachment attachment = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found: " + attachmentId
                ));

        if (!attachment.getIncidentId().equals(incidentId)) {
            throw new BadRequestException(
                    "Attachment does not belong to this incident"
            );
        }
        if (!"COMPLETED".equals(attachment.getRecognitionStatus())
                || attachment.getAnnotatedFileUrl() == null
                || attachment.getAnnotatedFileUrl().isBlank()) {
            throw new ResourceNotFoundException(
                    "AI processed media is not available"
            );
        }
        if (rangeHeader != null
                && !rangeHeader.isBlank()
                && !rangeHeader.regionMatches(
                true,
                0,
                "bytes=",
                0,
                6
        )) {
            throw new BadRequestException("Invalid Range header");
        }

        HttpURLConnection connection = null;
        try {
            URI upstreamUri = resolveYoloMediaUri(
                    attachment.getAnnotatedFileUrl()
            );

            connection = (HttpURLConnection)
                    upstreamUri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(yoloConnectTimeout);
            connection.setReadTimeout(yoloReadTimeout);
            connection.setRequestProperty(
                    HttpHeaders.ACCEPT,
                    "*/*"
            );
            connection.setRequestProperty(
                    HttpHeaders.ACCEPT_ENCODING,
                    "identity"
            );

            if (rangeHeader != null && !rangeHeader.isBlank()) {
                connection.setRequestProperty(
                        HttpHeaders.RANGE,
                        rangeHeader.trim()
                );
            }

            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK
                    && statusCode != HttpURLConnection.HTTP_PARTIAL
                    && statusCode != 416) {
                connection.disconnect();
                throw new ExternalServiceException(
                        "YOLO media service returned HTTP "
                                + statusCode
                );
            }

            String contentType = resolveMediaContentType(
                    connection.getContentType(),
                    attachment,
                    upstreamUri
            );
            long contentLength = connection.getContentLengthLong();
            String contentRange = connection.getHeaderField(
                    HttpHeaders.CONTENT_RANGE
            );
            String acceptRanges = connection.getHeaderField(
                    HttpHeaders.ACCEPT_RANGES
            );
            String etag = connection.getHeaderField(
                    HttpHeaders.ETAG
            );
            long lastModified = connection.getLastModified();

            HttpURLConnection activeConnection = connection;
            StreamingResponseBody body = outputStream -> {
                try (InputStream inputStream = statusCode >= 400
                        ? activeConnection.getErrorStream()
                        : activeConnection.getInputStream()) {
                    if (inputStream != null) {
                        inputStream.transferTo(outputStream);
                    }
                    outputStream.flush();
                } finally {
                    activeConnection.disconnect();
                }
            };

            return new AiAttachmentMediaStream(
                    statusCode,
                    contentType,
                    contentLength,
                    contentRange,
                    acceptRanges == null || acceptRanges.isBlank()
                            ? "bytes"
                            : acceptRanges,
                    etag,
                    lastModified,
                    body
            );
        } catch (IOException | IllegalArgumentException ex) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new ExternalServiceException(
                    "Failed to read AI processed media: "
                            + ex.getMessage(),
                    ex
            );
        }
    }

    private URI resolveYoloMediaUri(
            String storedUrl
    ) {
        URI configuredBase = URI.create(
                yoloBaseUrl.endsWith("/")
                        ? yoloBaseUrl
                        : yoloBaseUrl + "/"
        );
        URI storedUri = URI.create(storedUrl.trim());

        if (!storedUri.isAbsolute()) {
            String relative = storedUrl.startsWith("/")
                    ? storedUrl.substring(1)
                    : storedUrl;
            return configuredBase.resolve(relative);
        }

        String path = storedUri.getRawPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid AI processed media URL"
            );
        }

        try {
            return new URI(
                    configuredBase.getScheme(),
                    configuredBase.getUserInfo(),
                    configuredBase.getHost(),
                    configuredBase.getPort(),
                    path,
                    storedUri.getRawQuery(),
                    null
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid AI processed media URL",
                    ex
            );
        }
    }

    private String resolveMediaContentType(
            String upstreamContentType,
            IncidentAttachment attachment,
            URI upstreamUri
    ) {
        if (upstreamContentType != null
                && !upstreamContentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(
                upstreamContentType
        )) {
            return upstreamContentType;
        }

        String path = upstreamUri.getPath() == null
                ? ""
                : upstreamUri.getPath().toLowerCase();
        if (path.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (path.endsWith(".webm")) {
            return "video/webm";
        }
        if (path.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }

        return attachment.getContentType() == null
                || attachment.getContentType().isBlank()
                ? "application/octet-stream"
                : attachment.getContentType();
    }

    public record AiAttachmentMediaStream(
            int statusCode,
            String contentType,
            long contentLength,
            String contentRange,
            String acceptRanges,
            String etag,
            long lastModified,
            StreamingResponseBody body
    ) {
    }

    /**
     * 指挥中心标记附件为已查看。
     */
    public void markAttachmentReviewed(Long incidentId, Long attachmentId) {
        IncidentAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!att.getIncidentId().equals(incidentId)) {
            throw new BadRequestException("Attachment does not belong to this incident");
        }
        att.setReviewed(true);
        attachmentRepository.save(att);
    }
}
