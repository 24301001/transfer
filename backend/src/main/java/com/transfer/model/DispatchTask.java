package com.transfer.model;

import com.transfer.enums.RiskLevel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_tasks")
public class DispatchTask extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String taskNo;

    @Column(nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskType taskType;

    private Long receiverUserId;

    private Long assignedByUserId;

    /** 清障中心主键 */
    private Long rescueCenterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.DISPATCHED;

    private Boolean vehicleRequired;

    @Column(length = 80)
    private String vehicleType;

    @Column(length = 160)
    private String locationName;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RiskLevel riskLevel;

    @Column(length = 1000)
    private String advice;

    @Column(length = 1000)
    private String feedback;

    private LocalDateTime completedAt;

    /**
     * 以下字段用于救护车/清障车调度与轨迹计算。
     */
    private Long emergencyVehicleId;

    @Column(length = 40)
    private String emergencyVehicleNo;

    @Column(length = 80)
    private String emergencyVehicleName;

    private Double vehicleStartLongitude;

    private Double vehicleStartLatitude;

    private Double vehicleStartBaiduLongitude;

    private Double vehicleStartBaiduLatitude;

    private Double incidentTargetLongitude;

    private Double incidentTargetLatitude;

    private Double incidentTargetBaiduLongitude;

    private Double incidentTargetBaiduLatitude;

    private Double dispatchDistanceKm;

    private Double dispatchSpeedKmh;

    private Integer estimatedArrivalMinutes;

    private LocalDateTime departedAt;

    private LocalDateTime arrivedAt;

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(Long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public Long getAssignedByUserId() {
        return assignedByUserId;
    }

    public void setAssignedByUserId(Long assignedByUserId) {
        this.assignedByUserId = assignedByUserId;
    }

    public Long getRescueCenterId() {
        return rescueCenterId;
    }

    public void setRescueCenterId(Long rescueCenterId) {
        this.rescueCenterId = rescueCenterId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Boolean getVehicleRequired() {
        return vehicleRequired;
    }

    public void setVehicleRequired(Boolean vehicleRequired) {
        this.vehicleRequired = vehicleRequired;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getEmergencyVehicleId() {
        return emergencyVehicleId;
    }

    public void setEmergencyVehicleId(Long emergencyVehicleId) {
        this.emergencyVehicleId = emergencyVehicleId;
    }

    public String getEmergencyVehicleNo() {
        return emergencyVehicleNo;
    }

    public void setEmergencyVehicleNo(String emergencyVehicleNo) {
        this.emergencyVehicleNo = emergencyVehicleNo;
    }

    public String getEmergencyVehicleName() {
        return emergencyVehicleName;
    }

    public void setEmergencyVehicleName(String emergencyVehicleName) {
        this.emergencyVehicleName = emergencyVehicleName;
    }

    public Double getVehicleStartLongitude() {
        return vehicleStartLongitude;
    }

    public void setVehicleStartLongitude(Double vehicleStartLongitude) {
        this.vehicleStartLongitude = vehicleStartLongitude;
    }

    public Double getVehicleStartLatitude() {
        return vehicleStartLatitude;
    }

    public void setVehicleStartLatitude(Double vehicleStartLatitude) {
        this.vehicleStartLatitude = vehicleStartLatitude;
    }

    public Double getVehicleStartBaiduLongitude() {
        return vehicleStartBaiduLongitude;
    }

    public void setVehicleStartBaiduLongitude(Double vehicleStartBaiduLongitude) {
        this.vehicleStartBaiduLongitude = vehicleStartBaiduLongitude;
    }

    public Double getVehicleStartBaiduLatitude() {
        return vehicleStartBaiduLatitude;
    }

    public void setVehicleStartBaiduLatitude(Double vehicleStartBaiduLatitude) {
        this.vehicleStartBaiduLatitude = vehicleStartBaiduLatitude;
    }

    public Double getIncidentTargetLongitude() {
        return incidentTargetLongitude;
    }

    public void setIncidentTargetLongitude(Double incidentTargetLongitude) {
        this.incidentTargetLongitude = incidentTargetLongitude;
    }

    public Double getIncidentTargetLatitude() {
        return incidentTargetLatitude;
    }

    public void setIncidentTargetLatitude(Double incidentTargetLatitude) {
        this.incidentTargetLatitude = incidentTargetLatitude;
    }

    public Double getIncidentTargetBaiduLongitude() {
        return incidentTargetBaiduLongitude;
    }

    public void setIncidentTargetBaiduLongitude(Double incidentTargetBaiduLongitude) {
        this.incidentTargetBaiduLongitude = incidentTargetBaiduLongitude;
    }

    public Double getIncidentTargetBaiduLatitude() {
        return incidentTargetBaiduLatitude;
    }

    public void setIncidentTargetBaiduLatitude(Double incidentTargetBaiduLatitude) {
        this.incidentTargetBaiduLatitude = incidentTargetBaiduLatitude;
    }

    public Double getDispatchDistanceKm() {
        return dispatchDistanceKm;
    }

    public void setDispatchDistanceKm(Double dispatchDistanceKm) {
        this.dispatchDistanceKm = dispatchDistanceKm;
    }

    public Double getDispatchSpeedKmh() {
        return dispatchSpeedKmh;
    }

    public void setDispatchSpeedKmh(Double dispatchSpeedKmh) {
        this.dispatchSpeedKmh = dispatchSpeedKmh;
    }

    public Integer getEstimatedArrivalMinutes() {
        return estimatedArrivalMinutes;
    }

    public void setEstimatedArrivalMinutes(Integer estimatedArrivalMinutes) {
        this.estimatedArrivalMinutes = estimatedArrivalMinutes;
    }

    public LocalDateTime getDepartedAt() {
        return departedAt;
    }

    public void setDepartedAt(LocalDateTime departedAt) {
        this.departedAt = departedAt;
    }

    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(LocalDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }
}
