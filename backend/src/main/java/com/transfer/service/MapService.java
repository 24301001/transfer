package com.transfer.service;

import com.transfer.adapter.MapProvider;
import com.transfer.common.BadRequestException;
import com.transfer.common.ExternalServiceException;
import com.transfer.dto.MapClientConfigResponse;
import com.transfer.dto.MapLocationResponse;
import com.transfer.dto.MapPointResponse;
import com.transfer.enums.CoordinateType;
import com.transfer.model.Incident;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MapService {

    private final MapProvider mapProvider;
    private final String browserAk;

    public MapService(
            MapProvider mapProvider,

            @Value("${baidu.map.browser-ak:}")
            String browserAk
    ) {
        this.mapProvider = mapProvider;
        this.browserAk = browserAk == null
                ? ""
                : browserAk.trim();
    }

    public MapPointResponse convertToBaidu(
            Double longitude,
            Double latitude,
            CoordinateType coordinateType
    ) {
        MapProvider.MapPoint point =
                mapProvider.convertToBaidu(
                        longitude,
                        latitude,
                        coordinateType == null
                                ? CoordinateType.WGS84
                                : coordinateType
                );

        return new MapPointResponse(
                point.longitude(),
                point.latitude(),
                point.coordinateType()
        );
    }

    public MapLocationResponse reverseGeocode(
            Double longitude,
            Double latitude,
            CoordinateType coordinateType
    ) {
        CoordinateType sourceType =
                coordinateType == null
                        ? CoordinateType.WGS84
                        : coordinateType;

        MapProvider.MapLocation location =
                mapProvider.reverseGeocode(
                        longitude,
                        latitude,
                        sourceType
                );

        return toResponse(
                longitude,
                latitude,
                sourceType,
                location,
                "地图位置解析成功"
        );
    }

    public MapLocationResponse geocode(
            String address,
            String city
    ) {
        MapProvider.MapLocation location =
                mapProvider.geocode(address, city);

        MapProvider.MapPoint point = location.point();

        return toResponse(
                point.longitude(),
                point.latitude(),
                CoordinateType.BD09,
                location,
                "地址解析成功"
        );
    }

    /**
     * 给前端返回浏览器端 JS API 配置。
     */
    public MapClientConfigResponse clientConfig() {
        if (browserAk.isBlank()) {
            return new MapClientConfigResponse(
                    false,
                    "",
                    "",
                    "未配置浏览器端AK。"
                            + "百度地图JavaScript API必须使用浏览器端AK。"
            );
        }

        return new MapClientConfigResponse(
                true,
                browserAk,
                "https://api.map.baidu.com/api"
                        + "?v=1.0"
                        + "&type=webgl"
                        + "&ak="
                        + browserAk,
                "百度地图JavaScript API配置可用"
        );
    }

    /**
     * 为事故生成并保存百度坐标与标准地址。
     */
    public MapLocationResponse enrichIncident(
            Incident incident
    ) {
        if (incident == null) {
            throw new BadRequestException(
                    "incident is required"
            );
        }

        CoordinateType sourceType =
                incident.getCoordinateType() == null
                        ? CoordinateType.WGS84
                        : incident.getCoordinateType();

        /*
         * 数据库中已有转换结果时直接使用缓存。
         */
        if (incident.getBaiduLongitude() != null
                && incident.getBaiduLatitude() != null) {
            return fromStoredIncident(
                    incident,
                    "已使用事故中缓存的百度地图坐标"
            );
        }

        MapProvider.MapLocation location;

        /*
         * 情况一：事故有经纬度。
         */
        if (incident.getLongitude() != null
                && incident.getLatitude() != null) {

            MapProvider.MapPoint baiduPoint =
                    mapProvider.convertToBaidu(
                            incident.getLongitude(),
                            incident.getLatitude(),
                            sourceType
                    );

            incident.setBaiduLongitude(
                    baiduPoint.longitude()
            );

            incident.setBaiduLatitude(
                    baiduPoint.latitude()
            );

            incident.setMapFormattedAddress(
                    firstNonBlank(
                            incident.getMapFormattedAddress(),
                            incident.getAddress(),
                            incident.getLocationName()
                    )
            );

            try {
                location = mapProvider.reverseGeocode(
                        baiduPoint.longitude(),
                        baiduPoint.latitude(),
                        CoordinateType.BD09
                );
            } catch (ExternalServiceException ex) {
                return fromStoredIncident(
                        incident,
                        "百度坐标已生成，但地址解析失败: "
                                + ex.getMessage()
                );
            }

        /*
         * 情况二：没有经纬度，但有文字地址。
         */
        } else if (hasText(incident.getAddress())) {
            location = mapProvider.geocode(
                    incident.getAddress(),
                    null
            );

            incident.setLongitude(
                    location.point().longitude()
            );

            incident.setLatitude(
                    location.point().latitude()
            );

            incident.setCoordinateType(
                    CoordinateType.BD09
            );

            sourceType = CoordinateType.BD09;

        } else {
            return unavailable(
                    incident,
                    "事故没有经纬度，也没有可用于地理编码的地址"
            );
        }

        applyLocation(incident, location);

        return toResponse(
                incident.getLongitude(),
                incident.getLatitude(),
                sourceType,
                location,
                "事故地图坐标已生成"
        );
    }

    public MapLocationResponse fromStoredIncident(
            Incident incident,
            String message
    ) {
        if (incident.getBaiduLongitude() == null
                || incident.getBaiduLatitude() == null) {
            return unavailable(
                    incident,
                    "事故尚未生成百度地图坐标"
            );
        }

        String navigationUrl =
                mapProvider.navigationUrl(
                        incident.getBaiduLongitude(),
                        incident.getBaiduLatitude(),
                        incident.getIncidentNo(),
                        firstNonBlank(
                                incident.getMapFormattedAddress(),
                                incident.getLocationName()
                        )
                );

        return new MapLocationResponse(
                true,
                message,

                incident.getLongitude(),
                incident.getLatitude(),
                incident.getCoordinateType(),

                incident.getBaiduLongitude(),
                incident.getBaiduLatitude(),

                firstNonBlank(
                        incident.getMapFormattedAddress(),
                        incident.getAddress(),
                        incident.getLocationName()
                ),

                incident.getMapSemanticDescription(),

                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,

                navigationUrl
        );
    }

    private void applyLocation(
            Incident incident,
            MapProvider.MapLocation location
    ) {
        incident.setBaiduLongitude(
                location.point().longitude()
        );

        incident.setBaiduLatitude(
                location.point().latitude()
        );

        incident.setMapFormattedAddress(
                firstNonBlank(
                        location.formattedAddress(),
                        incident.getAddress(),
                        incident.getLocationName()
                )
        );

        incident.setMapSemanticDescription(
                location.semanticDescription()
        );

        if (!hasText(incident.getAddress())
                && hasText(location.formattedAddress())) {
            incident.setAddress(
                    location.formattedAddress()
            );
        }
    }

    private MapLocationResponse toResponse(
            Double sourceLongitude,
            Double sourceLatitude,
            CoordinateType sourceType,
            MapProvider.MapLocation location,
            String message
    ) {
        String navigationUrl =
                mapProvider.navigationUrl(
                        location.point().longitude(),
                        location.point().latitude(),
                        "事故位置",
                        location.formattedAddress()
                );

        return new MapLocationResponse(
                true,
                message,

                sourceLongitude,
                sourceLatitude,
                sourceType,

                location.point().longitude(),
                location.point().latitude(),

                location.formattedAddress(),
                location.semanticDescription(),

                location.province(),
                location.city(),
                location.district(),
                location.town(),
                location.street(),
                location.streetNumber(),
                location.business(),
                location.adcode(),

                navigationUrl
        );
    }

    private MapLocationResponse unavailable(
            Incident incident,
            String message
    ) {
        return new MapLocationResponse(
                false,
                message,

                incident.getLongitude(),
                incident.getLatitude(),
                incident.getCoordinateType(),

                incident.getBaiduLongitude(),
                incident.getBaiduLatitude(),

                firstNonBlank(
                        incident.getMapFormattedAddress(),
                        incident.getAddress(),
                        incident.getLocationName()
                ),

                incident.getMapSemanticDescription(),

                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,

                ""
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
}
