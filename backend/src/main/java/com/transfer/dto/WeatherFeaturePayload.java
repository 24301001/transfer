package com.transfer.dto;

import com.transfer.enums.CoordinateType;

/**
 * 百度地图实时天气中与事故预测最相关的结构化特征。
 */
public record WeatherFeaturePayload(
        String source,
        Double longitude,
        Double latitude,
        CoordinateType coordinateType,

        String country,
        String province,
        String city,
        String district,
        String districtId,

        String text,
        Integer temperatureC,
        Integer feelsLikeC,
        Integer humidityPercent,

        String windDirection,
        String windClass,

        Double precipitation1hMm,
        Integer cloudPercent,
        Integer visibilityMeters,
        Integer aqi,

        String updateTime
) {
}
