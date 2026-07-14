package com.transfer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.transfer.enums.CoordinateType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 事故上报请求。
 *
 * <p>保留后端既有字段名，同时通过 {@link JsonAlias} 兼容常见前端字段，
 * 让旧页面可以逐步迁移，而不改变 IncidentService 的核心业务逻辑。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateIncidentRequest(
        @JsonAlias({"location", "locationText", "placeName", "incidentLocation"})
        @NotBlank
        @Size(max = 160)
        String locationName,

        @JsonAlias({"detailAddress", "formattedAddress"})
        @Size(max = 255)
        String address,

        @JsonAlias({"lng", "lon"})
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,

        @JsonAlias({"lat"})
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        /*
         * 手机 GPS 一般传 WGS84。
         * 高德/腾讯地图选点一般传 GCJ02。
         * 已经是百度坐标则传 BD09。
         */
        @JsonAlias({"coordType", "coordinateSystem"})
        CoordinateType coordinateType,

        @JsonAlias({"road", "streetName"})
        @Size(max = 80)
        String roadName,

        @JsonAlias({"accidentType", "incidentType"})
        @Size(max = 80)
        String initialAccidentType,

        @JsonAlias({"labels", "sceneTags"})
        @Size(max = 500)
        String sceneLabels,

        @JsonAlias({"detail", "details", "incidentDescription", "remark"})
        @NotBlank
        @Size(max = 1000)
        String description,

        @JsonAlias({"lanesOccupied", "occupiedLaneCount"})
        @Min(0)
        Integer occupiedLanes,

        @JsonAlias({"vehicleFlow", "trafficVolume"})
        @Min(0)
        Integer trafficFlow,

        @JsonAlias({"pedestrianFlow", "crowdFlow"})
        @Min(0)
        Integer peopleFlow,

        @JsonAlias({"weatherCondition"})
        @Size(max = 40)
        String weather,

        @JsonAlias({"roadGrade"})
        @Size(max = 40)
        String roadLevel,

        @JsonAlias({"roadCondition"})
        @Size(max = 40)
        String roadStatus,

        @JsonAlias({"involvedPeople", "peopleCount"})
        @Min(0)
        Integer peopleInvolved,

        @JsonAlias({"injuries", "injuryCount"})
        @Min(0)
        Integer injuredCount,

        @JsonAlias({"hasInjury", "injured"})
        Boolean injuryReported,

        @JsonAlias({"injuryDescription", "injuryInfo"})
        @Size(max = 500)
        String injuryEstimate,

        @JsonAlias({"reporterId", "userId"})
        Long reportUserId
) {
}
