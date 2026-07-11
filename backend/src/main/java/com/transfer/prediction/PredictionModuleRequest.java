package com.transfer.prediction;

import com.transfer.dto.PredictionAttachmentPayload;

import java.util.List;

/**
 * 发送给数据预测模块的请求体。
 *
 * <p>包含事故基本信息、附件路径列表，以及需要执行的算法集合。</p>
 */
public record PredictionModuleRequest(
        Long incidentId,
        String incidentNo,
        List<PredictionAlgorithmType> algorithmTypes,
        String locationName,
        String address,
        String description,
        Double longitude,
        Double latitude,
        Integer occupiedLanes,
        Integer trafficFlow,
        Integer peopleFlow,
        String weather,
        String roadLevel,
        String roadName,
        List<PredictionAttachmentPayload> attachments
) {
}
