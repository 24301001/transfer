package com.transfer.dto;

import com.transfer.enums.TaskStatus;
import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;

public record VehicleTrackResponse(
        Long taskId,
        Long incidentId,

        Long vehicleId,
        String vehicleNo,
        String vehicleName,
        VehicleType vehicleType,

        TaskStatus taskStatus,
        VehicleStatus vehicleStatus,

        Double currentLongitude,
        Double currentLatitude,
        Double currentBaiduLongitude,
        Double currentBaiduLatitude,

        Double targetLongitude,
        Double targetLatitude,
        Double targetBaiduLongitude,
        Double targetBaiduLatitude,

        Double distanceKm,
        Double progressPercent,
        Integer estimatedArrivalMinutes,
        Integer remainingMinutes,

        String message
) {
}