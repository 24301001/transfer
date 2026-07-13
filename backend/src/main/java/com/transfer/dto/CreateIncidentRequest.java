package com.transfer.dto;

import com.transfer.enums.CoordinateType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotBlank
        @Size(max = 160)
        String locationName,

        @Size(max = 255)
        String address,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        /*
         * 手机 GPS 一般传 WGS84。
         * 高德/腾讯地图选点一般传 GCJ02。
         * 已经是百度坐标则传 BD09。
         */
        CoordinateType coordinateType,

        @Size(max = 80)
        String roadName,

        @Size(max = 80)
        String initialAccidentType,

        @Size(max = 500)
        String sceneLabels,

        @NotBlank
        @Size(max = 1000)
        String description,

        @Min(0)
        Integer occupiedLanes,

        @Min(0)
        Integer trafficFlow,

        @Min(0)
        Integer peopleFlow,

        @Size(max = 40)
        String weather,

        @Size(max = 40)
        String roadLevel,

        @Size(max = 40)
        String roadStatus,

        @Min(0)
        Integer peopleInvolved,

        @Min(0)
        Integer injuredCount,

        Boolean injuryReported,

        @Size(max = 500)
        String injuryEstimate,

        Long reportUserId
) {
}
