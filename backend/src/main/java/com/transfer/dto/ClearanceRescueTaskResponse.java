package com.transfer.dto;

import com.transfer.enums.CoordinateType;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.TaskType;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;

import java.time.LocalDateTime;

public record ClearanceRescueTaskResponse(
        Long taskId,
        String taskNo,
        TaskType taskType,
        TaskStatus status,

        Long receiverUserId,
        Long assignedByUserId,

        Boolean vehicleRequired,
        String vehicleType,
        Long emergencyVehicleId,
        String emergencyVehicleNo,
        String emergencyVehicleName,
        Double dispatchDistanceKm,
        Double dispatchSpeedKmh,
        Integer estimatedArrivalMinutes,

        LocalDateTime departedAt,
        LocalDateTime arrivedAt,
        LocalDateTime completedAt,

        Long incidentId,
        String incidentNo,
        String locationName,
        String address,
        Double longitude,
        Double latitude,
        CoordinateType coordinateType,
        Double baiduLongitude,
        Double baiduLatitude,
        String mapFormattedAddress,
        String navigationUrl,

        String accidentType,
        RiskLevel riskLevel,
        Boolean supportRequired,
        String supportReason,
        String roadName,
        String roadStatus,
        Integer occupiedLanes,
        String weather,

        String confirmedClearanceAdvice,
        String taskAdvice,
        String feedback,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ClearanceRescueTaskResponse from(
            DispatchTask task,
            Incident incident,
            MapLocationResponse map,
            String confirmedClearanceAdvice
    ) {
        return new ClearanceRescueTaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getTaskType(),
                task.getStatus(),

                task.getReceiverUserId(),
                task.getAssignedByUserId(),

                task.getVehicleRequired(),
                task.getVehicleType(),
                task.getEmergencyVehicleId(),
                task.getEmergencyVehicleNo(),
                task.getEmergencyVehicleName(),
                task.getDispatchDistanceKm(),
                task.getDispatchSpeedKmh(),
                task.getEstimatedArrivalMinutes(),

                task.getDepartedAt(),
                task.getArrivedAt(),
                task.getCompletedAt(),

                incident.getId(),
                incident.getIncidentNo(),
                incident.getLocationName(),
                incident.getAddress(),
                incident.getLongitude(),
                incident.getLatitude(),
                incident.getCoordinateType(),
                incident.getBaiduLongitude(),
                incident.getBaiduLatitude(),
                incident.getMapFormattedAddress(),
                map == null ? null : map.navigationUrl(),

                firstNonBlank(
                        incident.getConfirmedAccidentType(),
                        incident.getInitialAccidentType(),
                        "待识别"
                ),
                incident.getRiskLevel(),
                incident.getSupportRequired(),
                incident.getSupportReason(),
                incident.getRoadName(),
                incident.getRoadStatus(),
                incident.getOccupiedLanes(),
                incident.getWeather(),

                confirmedClearanceAdvice,
                task.getAdvice(),
                task.getFeedback(),

                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private static String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}
