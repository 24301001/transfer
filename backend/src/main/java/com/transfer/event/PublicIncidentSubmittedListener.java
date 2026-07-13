package com.transfer.event;

import com.transfer.model.PredictionResult;
import com.transfer.prediction.PredictionPipelineService;
import com.transfer.service.IncidentService;
import com.transfer.service.OperationLogService;
import com.transfer.service.RealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 市民事故上报后的后台 AI 处理器。
 *
 * <p>先执行 YOLOv5 附件识别，再执行数据预测模块。任一阶段失败，
 * 都不会回滚已经完成的市民事故上报和即时建议返回。</p>
 */
@Component
public class PublicIncidentSubmittedListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PublicIncidentSubmittedListener.class
            );

    private final IncidentService incidentService;
    private final PredictionPipelineService predictionPipelineService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    public PublicIncidentSubmittedListener(
            IncidentService incidentService,
            PredictionPipelineService predictionPipelineService,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.incidentService = incidentService;
        this.predictionPipelineService = predictionPipelineService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
    }

    @Async("incidentPredictionExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            PublicIncidentSubmittedEvent event
    ) {
        Long incidentId = event.incidentId();
        Long operatorUserId = event.operatorUserId();

        realtimeService.publish(
                "INCIDENT_AI_PROCESSING",
                Map.of(
                        "incidentId", incidentId,
                        "status", "PROCESSING",
                        "message", "事故识别与预测正在后台处理"
                )
        );

        /*
         * YOLO 失败时仍继续执行结构化预测模块，
         * 防止单个外部服务故障阻断整条后台链路。
         */
        try {
            incidentService.runYoloDetectionByIncidentId(
                    incidentId
            );

            realtimeService.publish(
                    "INCIDENT_YOLO_COMPLETED",
                    Map.of(
                            "incidentId", incidentId,
                            "status", "COMPLETED"
                    )
            );
        } catch (Exception exception) {
            log.error(
                    "事故 YOLOv5 后台检测失败，incidentId={}",
                    incidentId,
                    exception
            );

            operationLogService.record(
                    operatorUserId,
                    "BACKGROUND_YOLO_FAILED",
                    "Incident",
                    String.valueOf(incidentId),
                    null,
                    safeMessage(exception)
            );

            realtimeService.publish(
                    "INCIDENT_YOLO_FAILED",
                    Map.of(
                            "incidentId", incidentId,
                            "status", "FAILED",
                            "message", safeMessage(exception)
                    )
            );
        }

        try {
            PredictionResult result =
                    predictionPipelineService.execute(
                            incidentId,
                            operatorUserId
                    );

            if (result == null) {
                publishPredictionFailed(
                        incidentId,
                        operatorUserId,
                        "预测模块未返回可保存的结果"
                );
                return;
            }

            realtimeService.publish(
                    "INCIDENT_PREDICTION_COMPLETED",
                    Map.of(
                            "incidentId", incidentId,
                            "predictionResultId", result.getId(),
                            "status", "COMPLETED",
                            "message", "事故预测已完成"
                    )
            );
        } catch (Exception exception) {
            log.error(
                    "事故预测后台处理失败，incidentId={}",
                    incidentId,
                    exception
            );

            publishPredictionFailed(
                    incidentId,
                    operatorUserId,
                    safeMessage(exception)
            );
        }
    }

    private void publishPredictionFailed(
            Long incidentId,
            Long operatorUserId,
            String message
    ) {
        operationLogService.record(
                operatorUserId,
                "BACKGROUND_PREDICTION_FAILED",
                "Incident",
                String.valueOf(incidentId),
                null,
                message
        );

        realtimeService.publish(
                "INCIDENT_PREDICTION_FAILED",
                Map.of(
                        "incidentId", incidentId,
                        "status", "FAILED",
                        "message", message
                )
        );
    }

    private String safeMessage(Exception exception) {
        if (exception == null
                || exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return "后台处理失败";
        }

        String message = exception.getMessage().trim();
        return message.length() <= 500
                ? message
                : message.substring(0, 500);
    }
}
