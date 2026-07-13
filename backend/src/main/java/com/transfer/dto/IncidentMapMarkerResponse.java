package com.transfer.dto;

import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;

public record IncidentMapMarkerResponse(
        Long incidentId,
        String incidentNo,
        String label,

        String accidentType,
        RiskLevel riskLevel,
        Boolean supportRequired,
        IncidentStatus status,

        /*
         * 这里返回的已经是百度 BD09 坐标，
         * 前端可直接传给 BMapGL.Point。
         */
        Double longitude,
        Double latitude,

        boolean mapReady,
        String mapMessage
) {
}
