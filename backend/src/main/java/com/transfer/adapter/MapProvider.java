package com.transfer.adapter;

import com.transfer.enums.CoordinateType;

public interface MapProvider {

    /**
     * 根据文字地址获取百度坐标。
     */
    MapLocation geocode(
            String address,
            String city
    );

    /**
     * 根据经纬度获取地址。
     */
    MapLocation reverseGeocode(
            Double longitude,
            Double latitude,
            CoordinateType sourceCoordinateType
    );

    /**
     * 把 WGS84 或 GCJ02 坐标转换为百度 BD09 坐标。
     */
    MapPoint convertToBaidu(
            Double longitude,
            Double latitude,
            CoordinateType sourceCoordinateType
    );

    /**
     * 生成可直接打开的百度地图位置标记链接。
     */
    String navigationUrl(
            Double baiduLongitude,
            Double baiduLatitude,
            String title,
            String content
    );

    /**
     * 生成车辆所在救援中心/医院
     * 到事故地点的百度驾车路线。
     *
     * 起点和终点均使用百度 BD09 坐标。
     */
    String drivingNavigationUrl(
            Double originBaiduLongitude,
            Double originBaiduLatitude,
            String originName,

            Double destinationBaiduLongitude,
            Double destinationBaiduLatitude,
            String destinationName
    );

    record MapPoint(
            Double longitude,
            Double latitude,
            CoordinateType coordinateType
    ) {
    }

    record MapLocation(
            MapPoint point,

            String formattedAddress,

            String semanticDescription,

            String province,

            String city,

            String district,

            String town,

            String street,

            String streetNumber,

            String business,

            Integer adcode
    ) {
    }
}
