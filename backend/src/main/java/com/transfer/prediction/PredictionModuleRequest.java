package com.transfer.prediction;

import com.transfer.dto.PredictionAttachmentPayload;
import com.transfer.dto.WeatherFeaturePayload;

import java.util.List;

/**
 * 发送给数据预测模块的请求体。
 *
 * 保留原有 weather 字符串，
 * 并新增 weatherDetail 结构化天气特征。
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

        List<PredictionAttachmentPayload> attachments,

        WeatherFeaturePayload weatherDetail
) {

    /**
     * 兼容现有 PredictionPipelineService 的旧构造方式。
     */
    public PredictionModuleRequest(
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
        this(
                incidentId,
                incidentNo,
                algorithmTypes,
                locationName,
                address,
                description,
                longitude,
                latitude,
                occupiedLanes,
                trafficFlow,
                peopleFlow,
                weather,
                roadLevel,
                roadName,
                attachments,
                null
        );
    }

    /**
     * 返回补充了实时天气的新请求对象。
     */
    public PredictionModuleRequest withWeatherDetail(
            String currentWeatherText,
            WeatherFeaturePayload detail
    ) {
        return new PredictionModuleRequest(
                incidentId,
                incidentNo,
                algorithmTypes,
                locationName,
                address,
                description,
                longitude,
                latitude,
                occupiedLanes,
                trafficFlow,
                peopleFlow,
                firstNonBlank(
                        currentWeatherText,
                        weather
                ),
                roadLevel,
                roadName,
                attachments,
                detail
        );
    }

    private static String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}
