package com.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.dto.WeatherFeaturePayload;
import com.transfer.enums.CoordinateType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Service
public class BaiduWeatherService {

    private static final Logger log =
            LoggerFactory.getLogger(BaiduWeatherService.class);

    /**
     * 百度天气接口使用 999999 表示无效数值。
     */
    private static final int INVALID_NUMBER = 999999;

    private final RestClient restClient;
    private final String serverAk;

    public BaiduWeatherService(
            @Value("${baidu.map.base-url:https://api.map.baidu.com}")
            String baseUrl,

            @Value("${baidu.map.server-ak:}")
            String serverAk
    ) {
        String normalizedBaseUrl =
                baseUrl == null || baseUrl.isBlank()
                        ? "https://api.map.baidu.com"
                        : baseUrl.trim();

        this.restClient = RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .build();

        this.serverAk =
                serverAk == null
                        ? ""
                        : serverAk.trim();
    }

    /**
     * 按事故经纬度查询百度地图实时天气。
     */
    public WeatherFeaturePayload getCurrentWeather(
            Double longitude,
            Double latitude,
            CoordinateType coordinateType
    ) {
        requireAk();
        validateCoordinate(longitude, latitude);

        CoordinateType resolvedType =
                coordinateType == null
                        ? CoordinateType.WGS84
                        : coordinateType;

        JsonNode root;

        try {
            root = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/weather/v1/")
                            .queryParam(
                                    "location",
                                    longitude + "," + latitude
                            )
                            .queryParam("data_type", "now")
                            .queryParam("output", "json")
                            .queryParam(
                                    "coordtype",
                                    toBaiduCoordType(resolvedType)
                            )
                            .queryParam("ak", serverAk)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException ex) {
            throw new ExternalServiceException(
                    "调用百度地图天气接口失败",
                    ex
            );
        }

        if (root == null) {
            throw new ExternalServiceException(
                    "百度地图天气接口返回空响应"
            );
        }

        /*
         * 百度天气 API 成功时 status 为 0。
         */
        int status = root.path("status").asInt(-1);

        if (status != 0) {
            String message = firstNonBlank(
                    root.path("message").asText(null),
                    root.path("msg").asText(null),
                    "未知错误"
            );

            throw new ExternalServiceException(
                    "百度地图天气查询失败，status="
                            + status
                            + "，message="
                            + message
            );
        }

        JsonNode result = root.path("result");

        /*
         * 百度示例响应使用 location。
         * 为兼容部分旧返回格式，同时尝试 address。
         */
        JsonNode location = result.path("location");

        if (location.isMissingNode() || location.isNull()) {
            location = result.path("address");
        }

        JsonNode now = result.path("now");

        if (now.isMissingNode() || now.isNull()) {
            throw new ExternalServiceException(
                    "百度地图天气接口未返回实时天气 now"
            );
        }

        return new WeatherFeaturePayload(
                "BAIDU_MAP",

                longitude,
                latitude,
                resolvedType,

                text(location, "country"),
                text(location, "province"),
                text(location, "city"),

                firstNonBlank(
                        text(location, "name"),
                        text(location, "district")
                ),

                text(location, "id"),

                text(now, "text"),
                integer(now, "temp"),
                integer(now, "feels_like"),
                integer(now, "rh"),

                text(now, "wind_dir"),
                text(now, "wind_class"),

                decimal(now, "prec_1h"),
                integer(now, "clouds"),
                integer(now, "vis"),
                integer(now, "aqi"),

                text(now, "uptime")
        );
    }

    /**
     * 预测链路使用的降级查询。
     *
     * 天气接口不可用时，不阻断事故预测，
     * 继续使用事故上报时填写的 weather 字段。
     */
    public Optional<WeatherFeaturePayload> tryGetCurrentWeather(
            Double longitude,
            Double latitude,
            CoordinateType coordinateType
    ) {
        if (longitude == null
                || latitude == null
                || serverAk.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    getCurrentWeather(
                            longitude,
                            latitude,
                            coordinateType
                    )
            );

        } catch (RuntimeException ex) {
            log.warn(
                    "天气查询失败，预测将使用事故上报中的天气字段: {}",
                    ex.getMessage()
            );

            return Optional.empty();
        }
    }

    private void requireAk() {
        if (serverAk.isBlank()) {
            throw new ExternalServiceException(
                    "未配置 baidu.map.server-ak"
            );
        }
    }

    private void validateCoordinate(
            Double longitude,
            Double latitude
    ) {
        if (longitude == null || latitude == null) {
            throw new BadRequestException(
                    "longitude and latitude are required"
            );
        }

        if (longitude < -180 || longitude > 180) {
            throw new BadRequestException(
                    "longitude must be between -180 and 180"
            );
        }

        if (latitude < -90 || latitude > 90) {
            throw new BadRequestException(
                    "latitude must be between -90 and 90"
            );
        }
    }

    private String toBaiduCoordType(
            CoordinateType coordinateType
    ) {
        return switch (coordinateType) {
            case WGS84 -> "wgs84";
            case GCJ02 -> "gcj02";
            case BD09 -> "bd09ll";
        };
    }

    private String text(
            JsonNode node,
            String field
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        String value =
                node.path(field).asText(null);

        if (value == null
                || value.isBlank()
                || "暂无".equals(value)) {
            return null;
        }

        return value.trim();
    }

    private Integer integer(
            JsonNode node,
            String field
    ) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }

        int value =
                node.path(field).asInt(INVALID_NUMBER);

        return value == INVALID_NUMBER
                ? null
                : value;
    }

    private Double decimal(
            JsonNode node,
            String field
    ) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }

        double value =
                node.path(field).asDouble(INVALID_NUMBER);

        return Double.compare(
                value,
                INVALID_NUMBER
        ) == 0
                ? null
                : value;
    }

    private String firstNonBlank(
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
