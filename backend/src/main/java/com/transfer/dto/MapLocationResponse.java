package com.transfer.dto;

import com.transfer.enums.CoordinateType;

public record MapLocationResponse(
        boolean mapReady,
        String message,

        Double sourceLongitude,
        Double sourceLatitude,
        CoordinateType sourceCoordinateType,

        Double baiduLongitude,
        Double baiduLatitude,

        String formattedAddress,
        String semanticDescription,

        String province,
        String city,
        String district,
        String town,
        String street,
        String streetNumber,
        String business,
        Integer adcode,

        String navigationUrl
) {
}
