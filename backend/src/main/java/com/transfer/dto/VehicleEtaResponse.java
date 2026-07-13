package com.transfer.dto;

import com.transfer.enums.CoordinateType;
import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;

public record VehicleEtaResponse(
        Long vehicleId,
        String vehicleNo,
        String vehicleName,
        VehicleType vehicleType,
        VehicleStatus status,

        Double longitude,
        Double latitude,
        CoordinateType coordinateType,

        Double baiduLongitude,
        Double baiduLatitude,

        Double speedKmh,
        Double distanceKm,
        Integer estimatedArrivalMinutes,

        Boolean fastest,
        String message
) {
}
