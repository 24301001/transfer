package com.transfer.dto;

import com.transfer.enums.CoordinateType;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;

import java.time.LocalDateTime;

public record CommandIncidentSummaryResponse(
        Long id,
        String incidentNo,

        String accidentType,
        RiskLevel riskLevel,

        Integer predictedCongestionMinutes,
        Integer predictedRecoveryMinutes,

        Boolean supportRequired,
        String supportReason,

        IncidentStatus status,

        String locationName,
        String address,

        Double longitude,
        Double latitude,
        CoordinateType coordinateType,

        Double baiduLongitude,
        Double baiduLatitude,

        String mapFormattedAddress,
        String navigationUrl,

        LocalDateTime reportTime,

        long dispatchTaskCount,
        long activeDispatchTaskCount
) {
}
