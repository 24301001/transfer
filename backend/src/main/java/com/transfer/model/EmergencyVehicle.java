package com.transfer.model;

import com.transfer.enums.CoordinateType;
import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "emergency_vehicles")
public class EmergencyVehicle extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String vehicleNo;

    @Column(nullable = false, length = 80)
    private String vehicleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    private Double longitude;

    private Double latitude;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CoordinateType coordinateType = CoordinateType.WGS84;

    private Double baiduLongitude;

    private Double baiduLatitude;

    /**
     * 当前平均行驶速度，单位：km/h。
     */
    private Double speedKmh;

    @Column(length = 255)
    private String currentAddress;

    /**
     * 当前绑定的调度任务。
     */
    private Long currentTaskId;

    @Column(length = 500)
    private String remark;

    public String getVehicleNo() {
        return vehicleNo;
    }

    public void setVehicleNo(String vehicleNo) {
        this.vehicleNo = vehicleNo;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public void setVehicleName(String vehicleName) {
        this.vehicleName = vehicleName;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public CoordinateType getCoordinateType() {
        return coordinateType;
    }

    public void setCoordinateType(CoordinateType coordinateType) {
        this.coordinateType = coordinateType;
    }

    public Double getBaiduLongitude() {
        return baiduLongitude;
    }

    public void setBaiduLongitude(Double baiduLongitude) {
        this.baiduLongitude = baiduLongitude;
    }

    public Double getBaiduLatitude() {
        return baiduLatitude;
    }

    public void setBaiduLatitude(Double baiduLatitude) {
        this.baiduLatitude = baiduLatitude;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress) {
        this.currentAddress = currentAddress;
    }

    public Long getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(Long currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
