package com.transfer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum CoordinateType {
    /**
     * GPS 原始坐标。
     */
    WGS84,

    /**
     * 高德地图、腾讯地图使用的坐标。
     */
    GCJ02,

    /**
     * 百度地图经纬度坐标。
     */
    BD09;

    /**
     * 兼容前端常见写法：wgs84、WGS-84、gcj-02、bd_09，以及中文地图名称。
     */
    @JsonCreator
    public static CoordinateType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.contains("百度")) {
            return BD09;
        }
        if (trimmed.contains("高德") || trimmed.contains("腾讯") || trimmed.contains("火星")) {
            return GCJ02;
        }
        if (trimmed.toUpperCase(Locale.ROOT).contains("GPS")) {
            return WGS84;
        }

        String normalized = trimmed
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");

        return switch (normalized) {
            case "WGS84" -> WGS84;
            case "GCJ02", "GCJ2" -> GCJ02;
            case "BD09", "BD9" -> BD09;
            default -> throw new IllegalArgumentException("Unsupported coordinateType: " + value);
        };
    }
}
