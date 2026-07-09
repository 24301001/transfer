package com.transfer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.enums.CoordinateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class BaiduMapProvider implements MapProvider {

    private final RestClient restClient;
    private final String serverAk;

    public BaiduMapProvider(
            @Value("${baidu.map.base-url:https://api.map.baidu.com}")
            String baseUrl,

            @Value("${baidu.map.server-ak:}")
            String serverAk
    ) {
        // 百度地图 API 返回 Content-Type: text/javascript 而非 application/json，
        // 需要注册自定义 MessageConverter 以支持解析。
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("text", "javascript")
        ));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> converters.add(0, converter))
                .build();

        this.serverAk = serverAk == null ? "" : serverAk.trim();
    }

    @Override
    public MapLocation geocode(String address, String city) {
        requireAk();

        if (address == null || address.isBlank()) {
            throw new BadRequestException("address is required");
        }

        JsonNode root = execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/geocoding/v3/")
                            .queryParam("address", address.trim())
                            .queryParam("output", "json")
                            .queryParam("ak", serverAk);

                    if (city != null && !city.isBlank()) {
                        builder.queryParam("city", city.trim());
                    }

                    return builder.build();
                })
                .retrieve()
                .body(JsonNode.class));

        ensureSuccess(root, "百度地图地理编码");

        JsonNode result = required(
                root,
                "result",
                "百度地图地理编码没有返回结果"
        );

        JsonNode location = required(
                result,
                "location",
                "百度地图地理编码没有返回坐标"
        );

        MapPoint point = new MapPoint(
                readDouble(location, "lng"),
                readDouble(location, "lat"),
                CoordinateType.BD09
        );

        String formattedAddress = firstNonBlank(
                text(result, "formatted_address"),
                address.trim()
        );

        return new MapLocation(
                point,
                formattedAddress,
                null,
                null,
                city,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public MapLocation reverseGeocode(
            Double longitude,
            Double latitude,
            CoordinateType sourceCoordinateType
    ) {
        requireCoordinates(longitude, latitude);

        MapPoint baiduPoint = convertToBaidu(
                longitude,
                latitude,
                sourceCoordinateType
        );

        requireAk();

        JsonNode root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/reverse_geocoding/v3/")
                        .queryParam("ak", serverAk)
                        .queryParam("output", "json")
                        .queryParam("coordtype", "bd09ll")
                        .queryParam("extensions_poi", 1)
                        .queryParam("extensions_road", true)
                        .queryParam(
                                "location",
                                baiduPoint.latitude()
                                        + ","
                                        + baiduPoint.longitude()
                        )
                        .build())
                .retrieve()
                .body(JsonNode.class));

        ensureSuccess(root, "百度地图逆地理编码");

        JsonNode result = required(
                root,
                "result",
                "百度地图逆地理编码没有返回结果"
        );

        JsonNode addressComponent = result.path("addressComponent");

        String formattedAddress = firstNonBlank(
                text(result, "formatted_address_poi"),
                text(result, "formatted_address")
        );

        return new MapLocation(
                baiduPoint,
                formattedAddress,
                text(result, "sematic_description"),
                text(addressComponent, "province"),
                text(addressComponent, "city"),
                text(addressComponent, "district"),
                text(addressComponent, "town"),
                text(addressComponent, "street"),
                text(addressComponent, "street_number"),
                text(result, "business"),
                integer(addressComponent, "adcode")
        );
    }

    @Override
    public MapPoint convertToBaidu(
            Double longitude,
            Double latitude,
            CoordinateType sourceCoordinateType
    ) {
        requireCoordinates(longitude, latitude);

        CoordinateType sourceType = sourceCoordinateType == null
                ? CoordinateType.WGS84
                : sourceCoordinateType;

        if (sourceType == CoordinateType.BD09) {
            return new MapPoint(
                    longitude,
                    latitude,
                    CoordinateType.BD09
            );
        }

        requireAk();

        /*
         * model=1：高德/腾讯 GCJ02 转百度 BD09
         * model=2：GPS WGS84 转百度 BD09
         */
        int model = sourceType == CoordinateType.GCJ02 ? 1 : 2;

        JsonNode root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geoconv/v2/")
                        .queryParam(
                                "coords",
                                longitude + "," + latitude
                        )
                        .queryParam("model", model)
                        .queryParam("output", "json")
                        .queryParam("ak", serverAk)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        ensureSuccess(root, "百度地图坐标转换");

        JsonNode results = required(
                root,
                "result",
                "百度地图坐标转换没有返回结果"
        );

        if (!results.isArray() || results.isEmpty()) {
            throw new ExternalServiceException(
                    "百度地图坐标转换没有返回有效坐标"
            );
        }

        JsonNode point = results.get(0);

        return new MapPoint(
                readDouble(point, "x"),
                readDouble(point, "y"),
                CoordinateType.BD09
        );
    }

    @Override
    public String navigationUrl(
            Double baiduLongitude,
            Double baiduLatitude,
            String title,
            String content
    ) {
        if (baiduLongitude == null || baiduLatitude == null) {
            return "";
        }

        return "https://api.map.baidu.com/marker"
                + "?location="
                + baiduLatitude
                + ","
                + baiduLongitude
                + "&title="
                + encode(firstNonBlank(title, "事故位置"))
                + "&content="
                + encode(firstNonBlank(content, "查看事故位置"))
                + "&output=html"
                + "&src=traffic-accident-platform";
    }

    private JsonNode execute(RequestSupplier supplier) {
        try {
            JsonNode body = supplier.get();

            if (body == null) {
                throw new ExternalServiceException(
                        "百度地图服务返回空响应"
                );
            }

            return body;
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException(
                    "调用百度地图服务失败: " + ex.getMessage(),
                    ex
            );
        } catch (Exception ex) {
            throw new ExternalServiceException(
                    "处理百度地图响应失败: " + ex.getMessage(),
                    ex
            );
        }
    }

    private void ensureSuccess(JsonNode root, String operation) {
        int status = root.path("status").asInt(-1);

        if (status == 0) {
            return;
        }

        String message = firstNonBlank(
                text(root, "message"),
                text(root, "msg"),
                "未知错误"
        );

        throw new ExternalServiceException(
                operation
                        + "失败，status="
                        + status
                        + "，message="
                        + message
        );
    }

    private JsonNode required(
            JsonNode parent,
            String field,
            String message
    ) {
        JsonNode value = parent.path(field);

        if (value.isMissingNode() || value.isNull()) {
            throw new ExternalServiceException(message);
        }

        return value;
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);

        if (!value.isNumber()) {
            throw new ExternalServiceException(
                    "百度地图响应缺少数值字段: " + field
            );
        }

        return value.asDouble();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        String text = value.asText();

        return text == null || text.isBlank()
                ? null
                : text;
    }

    private void requireCoordinates(
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

    private void requireAk() {
        if (serverAk.isBlank()) {
            throw new ExternalServiceException(
                    "未配置百度地图服务端AK，"
                            + "请设置环境变量 BAIDU_MAP_SERVER_AK"
            );
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String firstNonBlank(String... values) {
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

    @FunctionalInterface
    private interface RequestSupplier {
        JsonNode get();
    }
}
