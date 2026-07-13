package com.transfer.prediction;

import com.transfer.dto.PredictionAttachmentPayload;
import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;
import com.transfer.repository.IncidentAttachmentRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.service.IncidentService;
import com.transfer.service.OperationLogService;
import com.transfer.service.RealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预测流水线编排服务。
 *
 * <p>职责：加载事故数据 → 构建预测请求 → 调用预测模块（含两个算法）→
 * 解析合并结果 → 持久化 PredictionResult → 更新 Incident → SSE 推送。</p>
 *
 * <p>这是预测端与后端对接的核心入口，取代直接调用 {@code IncidentService.submitPredictionRequest()}
 * 后等待异步回调的模式，改为同步调用预测模块并即时写入结果。</p>
 *
 * <h3>两种算法</h3>
 * <ul>
 *   <li><b>算法 A — 事故类型识别</b>：文本+图片 → accident_type、image_evidence</li>
 *   <li><b>算法 B — 风险影响评估</b>：结构化特征 → risk_level、congestion/recovery 时长</li>
 * </ul>
 */
@Service
public class PredictionPipelineService {

    private static final Logger log =
            LoggerFactory.getLogger(PredictionPipelineService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentAttachmentRepository attachmentRepository;
    private final PredictionModelClient predictionModelClient;
    private final IncidentService incidentService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    private final boolean syncMode;

    public PredictionPipelineService(
            IncidentRepository incidentRepository,
            IncidentAttachmentRepository attachmentRepository,
            PredictionModelClient predictionModelClient,
            IncidentService incidentService,
            OperationLogService operationLogService,
            RealtimeService realtimeService,
            @Value("${app.prediction-module.sync-mode:true}")
            boolean syncMode
    ) {
        this.incidentRepository = incidentRepository;
        this.attachmentRepository = attachmentRepository;
        this.predictionModelClient = predictionModelClient;
        this.incidentService = incidentService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
        this.syncMode = syncMode;
    }

    /**
     * 执行完整预测流水线。
     *
     * @param incidentId     事故 ID
     * @param operatorUserId 操作用户 ID
     * @return 持久化后的 PredictionResult，预测模块不可用时返回 null
     */
    /*
     * 不在整个方法上开启事务：外部预测调用可能持续约 1 分钟，
     * 长事务会长期占用数据库连接。最终结果写入仍由
     * IncidentService.acceptPredictionResult() 的短事务完成。
     */
    public PredictionResult execute(Long incidentId, Long operatorUserId) {
        if (incidentId == null) {
            log.warn("预测流水线收到 null incidentId，跳过");
            return null;
        }

        // 1. 加载事故 + 附件
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            log.warn("预测流水线找不到事故: incidentId={}", incidentId);
            return null;
        }

        List<IncidentAttachment> attachments =
                attachmentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);

        // 2. 构建请求
        PredictionModuleRequest request = buildRequest(incident, attachments);

        log.info("预测流水线启动: incidentId={}, incidentNo={}, algorithms={}, syncMode={}",
                incidentId, incident.getIncidentNo(), request.algorithmTypes(), syncMode);

        // 3. 调用预测模块
        PredictionModuleResponse response =
                predictionModelClient.predict(request);

        if (!response.isCompleted()) {
            log.warn("预测模块未完成: incidentId={}, status={}, error={}",
                    incidentId, response.status(), response.errorMessage());

            operationLogService.record(
                    operatorUserId,
                    "PREDICTION_PIPELINE_INCOMPLETE",
                    "Incident",
                    incident.getId().toString(),
                    null,
                    response.status() + ": " + response.errorMessage()
            );

            // 降级：回退到旧的异步提交模式
            if (!syncMode) {
                incidentService.submitPredictionRequest(incidentId, operatorUserId);
            }

            return null;
        }

        // 4. 合并两个算法结果为 PredictionModuleResultRequest
        PredictionModuleResultRequest merged =
                mergeResults(response, request);

        // 5. 写入数据库（复用 IncidentService 的现有逻辑）
        PredictionResult saved =
                incidentService.acceptPredictionResult(
                        incidentId, merged, operatorUserId);

        log.info("预测流水线完成: incidentId={}, accidentType={}, riskLevel={}, riskScore={}",
                incidentId,
                saved.getAccidentType(),
                saved.getRiskLevel(),
                saved.getRiskScore());

        return saved;
    }

    // ──────────────────────────── private helpers ────────────────────────────

    /**
     * 构建发送给预测模块的请求体。
     */
    private PredictionModuleRequest buildRequest(
            Incident incident,
            List<IncidentAttachment> attachments
    ) {
        List<PredictionAttachmentPayload> attachmentPayloads =
                attachments == null
                        ? List.of()
                        : attachments.stream()
                        .map(this::toAttachmentPayload)
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

    private PredictionAttachmentPayload toAttachmentPayload(
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
     * 将预测模块两个算法的结果合并为后端可接收的 {@link PredictionModuleResultRequest}。
     */
    private PredictionModuleResultRequest mergeResults(
            PredictionModuleResponse response,
            PredictionModuleRequest request
    ) {
        PredictionModuleResponse.PredictionModuleResponseResults results =
                response.results();

        /*
         * 算法 A — 事故类型识别
         */
        String accidentType;
        List<String> imageEvidence;
        String evidenceSummary;
        String modelVersionA;

        if (results != null && results.accidentType() != null) {
            AccidentTypeResult a = results.accidentType();
            accidentType = Objects.requireNonNullElse(
                    a.accidentType(), request.description());
            imageEvidence = a.imageEvidence();
            evidenceSummary = a.evidenceSummary();
            modelVersionA = a.modelVersion();
        } else {
            log.warn("算法 A（事故类型识别）未返回结果，使用原始事故类型降级");
            accidentType = request.description();
            imageEvidence = null;
            evidenceSummary = null;
            modelVersionA = null;
        }

        /*
         * 算法 B — 风险影响评估
         */
        RiskLevel riskLevel;
        Double riskScore;
        Integer congestionDurationMinutes;
        Integer recoveryDurationMinutes;
        Double confidenceB;
        String modelVersionB;
        List<String> riskFactors;
        String suggestion;
        String explanation;

        if (results != null && results.riskImpact() != null) {
            RiskImpactResult b = results.riskImpact();
            riskLevel = b.riskLevel() != null ? b.riskLevel() : RiskLevel.MEDIUM;
            riskScore = b.riskScore();
            congestionDurationMinutes = b.congestionDurationMinutes();
            recoveryDurationMinutes = b.recoveryDurationMinutes();
            confidenceB = b.confidence();
            modelVersionB = b.modelVersion();
            riskFactors = b.riskFactors();
            suggestion = b.suggestion();
            explanation = b.explanation();
        } else {
            log.warn("算法 B（风险影响评估）未返回结果，使用默认 MEDIUM 等级");
            riskLevel = RiskLevel.MEDIUM;
            riskScore = null;
            congestionDurationMinutes = null;
            recoveryDurationMinutes = null;
            confidenceB = null;
            modelVersionB = null;
            riskFactors = null;
            suggestion = null;
            explanation = null;
        }

        /*
         * 取两个算法中较高的置信度作为整体置信度。
         */
        Double confidence = max(
                results != null && results.accidentType() != null
                        ? results.accidentType().confidence() : null,
                confidenceB
        );

        /*
         * 模型版本取两个算法的拼接。
         */
        String modelVersion = joinVersions(modelVersionA, modelVersionB);

        return new PredictionModuleResultRequest(
                accidentType,
                riskLevel,
                riskScore,
                congestionDurationMinutes,
                recoveryDurationMinutes,
                confidence,
                modelVersion,
                riskFactors,
                imageEvidence,
                evidenceSummary,
                response.traceId(),
                null,  // rawResult — 完整 JSON 可能过大，按需存储
                suggestion,
                explanation
        );
    }

    private static Double max(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return a >= b ? a : b;
    }

    private static String joinVersions(String a, String b) {
        if (a == null && b == null) return "data-module";
        if (a == null) return b;
        if (b == null) return a;
        return a + "+" + b;
    }
}
