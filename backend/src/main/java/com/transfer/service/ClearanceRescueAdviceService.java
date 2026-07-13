package com.transfer.service;

import com.transfer.adapter.SiliconFlowClient;
import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.ClearanceRescueAdviceResponse;
import com.transfer.dto.ConfirmClearanceRescueAdviceRequest;
import com.transfer.enums.AdviceReviewStatus;
import com.transfer.model.ClearanceRescueAdvice;
import com.transfer.model.Incident;
import com.transfer.model.PredictionResult;
import com.transfer.repository.ClearanceRescueAdviceRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.PredictionResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class ClearanceRescueAdviceService {

    private final ClearanceRescueAdviceRepository
            adviceRepository;

    private final IncidentRepository
            incidentRepository;

    private final PredictionResultRepository
            predictionResultRepository;

    private final SiliconFlowClient
            siliconFlowClient;

    private final OperationLogService
            operationLogService;

    private final RealtimeService
            realtimeService;

    public ClearanceRescueAdviceService(
            ClearanceRescueAdviceRepository adviceRepository,
            IncidentRepository incidentRepository,
            PredictionResultRepository predictionResultRepository,
            SiliconFlowClient siliconFlowClient,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.adviceRepository =
                adviceRepository;

        this.incidentRepository =
                incidentRepository;

        this.predictionResultRepository =
                predictionResultRepository;

        this.siliconFlowClient =
                siliconFlowClient;

        this.operationLogService =
                operationLogService;

        this.realtimeService =
                realtimeService;
    }

    /**
     * 预测模块返回结果后自动调用。
     *
     * 生成一份等待指挥人员审核的建议。
     */
    @Transactional
    public ClearanceRescueAdviceResponse generateDraft(
            Incident incident,
            PredictionResult prediction,
            Long operatorUserId
    ) {
        if (incident == null
                || incident.getId() == null) {
            throw new BadRequestException(
                    "incident is required"
            );
        }

        if (prediction == null
                || prediction.getId() == null) {
            throw new BadRequestException(
                    "prediction result is required"
            );
        }

        String generatedAdvice =
                siliconFlowClient.chat(
                        buildSystemPrompt(),
                        buildUserPrompt(
                                incident,
                                prediction
                        ),
                        1200,
                        0.25
                );

        String generationSource =
                "SILICON_FLOW";

        /*
         * 硅基流动未配置或请求失败时，
         * 使用规则建议，不能影响预测结果正常保存。
         */
        if (generatedAdvice == null
                || generatedAdvice.isBlank()) {

            generatedAdvice =
                    buildFallbackAdvice(
                            incident,
                            prediction
                    );

            generationSource =
                    "RULE_FALLBACK";
        }

        ClearanceRescueAdvice advice =
                new ClearanceRescueAdvice();

        advice.setIncidentId(
                incident.getId()
        );

        advice.setPredictionResultId(
                prediction.getId()
        );

        advice.setAiAdvice(
                limit(generatedAdvice, 4000)
        );

        advice.setFinalAdvice(null);

        advice.setStatus(
                AdviceReviewStatus.DRAFT
        );

        advice.setGenerationSource(
                generationSource
        );

        advice.setModifiedByCommand(false);

        ClearanceRescueAdvice saved =
                adviceRepository.save(advice);

        operationLogService.record(
                operatorUserId,
                "GENERATE_CLEARANCE_RESCUE_ADVICE",
                "ClearanceRescueAdvice",
                saved.getId().toString(),
                null,
                generationSource
        );

        realtimeService.publish(
                "CLEARANCE_RESCUE_ADVICE_GENERATED",
                Map.of(
                        "incidentId",
                        saved.getIncidentId(),

                        "adviceId",
                        saved.getId(),

                        "status",
                        saved.getStatus().name()
                )
        );

        return ClearanceRescueAdviceResponse
                .from(saved);
    }

    /**
     * 指挥人员手动点击重新生成。
     *
     * 旧事故也可以通过这个方法补生成建议。
     */
    @Transactional
    public ClearanceRescueAdviceResponse
    generateLatestDraft(
            Long incidentId,
            Long operatorUserId
    ) {
        Incident incident =
                incidentRepository
                        .findById(incidentId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Incident not found: "
                                                        + incidentId
                                        )
                        );

        PredictionResult prediction =
                predictionResultRepository
                        .findFirstByIncidentIdOrderByCreatedAtDesc(
                                incidentId
                        )
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "该事故还没有预测结果，"
                                                        + "不能生成清障救援建议"
                                        )
                        );

        return generateDraft(
                incident,
                prediction,
                operatorUserId
        );
    }

    /**
     * 指挥中心查询最近生成的一条建议。
     */
    @Transactional(readOnly = true)
    public Optional<ClearanceRescueAdviceResponse>
    findLatest(
            Long incidentId
    ) {
        if (!incidentRepository.existsById(
                incidentId
        )) {
            throw new ResourceNotFoundException(
                    "Incident not found: "
                            + incidentId
            );
        }

        return adviceRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(
                        incidentId
                )
                .map(
                        ClearanceRescueAdviceResponse
                                ::from
                );
    }

    /**
     * 指挥人员直接采纳，或者修改后确认。
     */
    @Transactional
    public ClearanceRescueAdviceResponse confirm(
            Long incidentId,
            ConfirmClearanceRescueAdviceRequest request
    ) {
        ClearanceRescueAdvice advice =
                adviceRepository
                        .findById(
                                request.adviceId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Clearance rescue advice not found: "
                                                        + request.adviceId()
                                        )
                        );

        if (!incidentId.equals(
                advice.getIncidentId()
        )) {
            throw new ResourceNotFoundException(
                    "该建议不属于事故: "
                            + incidentId
            );
        }

        if (advice.getStatus()
                == AdviceReviewStatus.CONFIRMED) {

            throw new BadRequestException(
                    "该建议已经确认，不能重复确认"
            );
        }

        String finalAdvice =
                request.finalAdvice().trim();

        advice.setFinalAdvice(
                limit(finalAdvice, 4000)
        );

        advice.setStatus(
                AdviceReviewStatus.CONFIRMED
        );

        advice.setModifiedByCommand(
                !normalize(advice.getAiAdvice())
                        .equals(
                                normalize(finalAdvice)
                        )
        );

        advice.setConfirmedByUserId(
                request.confirmedByUserId()
        );

        advice.setConfirmedAt(
                LocalDateTime.now()
        );

        ClearanceRescueAdvice saved =
                adviceRepository.save(advice);

        operationLogService.record(
                request.confirmedByUserId(),
                "CONFIRM_CLEARANCE_RESCUE_ADVICE",
                "ClearanceRescueAdvice",
                saved.getId().toString(),
                null,

                Boolean.TRUE.equals(
                        saved.getModifiedByCommand()
                )
                        ? "指挥人员修改后确认"
                        : "指挥人员直接采纳 AI 建议"
        );

        /*
         * 后续救援模块可以监听这个 SSE 事件。
         */
        realtimeService.publish(
                "CLEARANCE_RESCUE_ADVICE_CONFIRMED",
                Map.of(
                        "incidentId",
                        saved.getIncidentId(),

                        "adviceId",
                        saved.getId(),

                        "confirmedByUserId",
                        saved.getConfirmedByUserId(),

                        "finalAdvice",
                        saved.getFinalAdvice()
                )
        );

        return ClearanceRescueAdviceResponse
                .from(saved);
    }

    /**
     * 给后续清障救援模块预留。
     *
     * 救援模块只能读取已经确认的建议。
     */
    @Transactional(readOnly = true)
    public Optional<ClearanceRescueAdviceResponse>
    findLatestConfirmed(
            Long incidentId
    ) {
        return adviceRepository
                .findFirstByIncidentIdAndStatusOrderByConfirmedAtDesc(
                        incidentId,
                        AdviceReviewStatus.CONFIRMED
                )
                .map(
                        ClearanceRescueAdviceResponse
                                ::from
                );
    }

    private String buildSystemPrompt() {
        return """
                你是道路交通事故清障救援指挥助手。
                请根据事故信息和预测模型结果，生成可直接交给清障救援人员执行的建议。

                要求：
                1. 只输出建议正文，不要输出 Markdown 标题、代码块或免责声明；
                2. 内容包括处置优先级、所需人员车辆设备、现场操作步骤、交通与人员安全要求、完成标准；
                3. 不得虚构具体人员姓名、车牌号或联系方式；
                4. 表述简洁、明确、可执行，控制在 800 字以内。
                """;
    }

    private String buildUserPrompt(
            Incident incident,
            PredictionResult prediction
    ) {
        return """
                事故编号：%s
                事故地点：%s
                道路：%s
                事故描述：%s
                事故类型：%s
                风险等级：%s
                风险分数：%s
                占用车道：%s
                预计拥堵时长：%s 分钟
                预计道路恢复时长：%s 分钟
                风险因素：%s
                模型处置建议：%s
                模型解释：%s
                """.formatted(
                safe(incident.getIncidentNo()),
                safe(incident.getLocationName()),
                safe(incident.getRoadName()),
                safe(incident.getDescription()),

                safe(prediction.getAccidentType()),
                prediction.getRiskLevel(),
                value(prediction.getRiskScore()),

                value(incident.getOccupiedLanes()),

                value(
                        prediction
                                .getCongestionDurationMinutes()
                ),

                value(
                        prediction
                                .getRecoveryDurationMinutes()
                ),

                safe(prediction.getRiskFactors()),
                safe(prediction.getSuggestions()),
                safe(prediction.getExplanation())
        );
    }

    /**
     * AI 不可用时的兜底建议。
     */
    private String buildFallbackAdvice(
            Incident incident,
            PredictionResult prediction
    ) {
        String priority =
                switch (prediction.getRiskLevel()) {
                    case LOW ->
                            "低优先级，先由现场人员核实车辆是否具备自行驶离条件";

                    case MEDIUM ->
                            "中优先级，安排清障车辆待命并尽快恢复受影响车道";

                    case HIGH ->
                            "高优先级，立即调度清障车辆和救援人员到场";

                    case CRITICAL ->
                            "最高优先级，联动清障、救援、医疗和交通管控力量";
                };

        return priority
                + "。建议携带拖车、警示锥桶、反光标志、"
                + "牵引及破拆设备；到场后先设置上游警戒区，"
                + "确认伤员、起火、泄漏和被困风险，"
                + "再按照先救人、后控险、再清障的顺序处置。"
                + "清理事故车辆及散落物后检查路面，"
                + "确认无泄漏、无障碍物且车道具备安全通行条件，"
                + "再向指挥中心反馈并申请解除管控。"
                + "事故地点："
                + safe(incident.getLocationName())
                + "；预计道路恢复时间："
                + value(
                        prediction
                                .getRecoveryDurationMinutes()
                )
                + "分钟。";
    }

    private String safe(String value) {
        return value == null
                || value.isBlank()
                ? "未提供"
                : value.trim();
    }

    private String value(Object value) {
        return value == null
                ? "未提供"
                : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value
                .replaceAll("\\s+", "")
                .trim();
    }

    private String limit(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        String text = value.trim();

        return text.length() <= maxLength
                ? text
                : text.substring(
                        0,
                        maxLength
                );
    }
}
