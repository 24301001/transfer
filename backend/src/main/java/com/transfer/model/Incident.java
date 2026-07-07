package com.transfer.model;

import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "incidents")
public class Incident extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String incidentNo;

    @Column(nullable = false, length = 160)
    private String locationName;

    @Column(length = 255)
    private String address;

    private Double longitude;

    private Double latitude;

    @Column(length = 80)
    private String roadName;

    @Column(length = 80)
    private String initialAccidentType;

    @Column(length = 80)
    private String confirmedAccidentType;

    @Column(nullable = false, length = 1000)
    private String description;

    private Integer occupiedLanes;

    private Integer trafficFlow;

    private Integer peopleFlow;

    @Column(length = 40)
    private String weather;

    @Column(length = 40)
    private String roadLevel;

    @Column(length = 80)
    private String roadStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status = IncidentStatus.REPORTED;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RiskLevel riskLevel;

    private Integer predictedCongestionMinutes;

    private Integer predictedRecoveryMinutes;

    private Double confidence;

    @Column(length = 1000)
    private String suggestion;

    @Column(length = 1500)
    private String explanation;

    private Long reportUserId;

    public String getIncidentNo() {
        return incidentNo;
    }

    public void setIncidentNo(String incidentNo) {
        this.incidentNo = incidentNo;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public String getInitialAccidentType() {
        return initialAccidentType;
    }

    public void setInitialAccidentType(String initialAccidentType) {
        this.initialAccidentType = initialAccidentType;
    }

    public String getConfirmedAccidentType() {
        return confirmedAccidentType;
    }

    public void setConfirmedAccidentType(String confirmedAccidentType) {
        this.confirmedAccidentType = confirmedAccidentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getOccupiedLanes() {
        return occupiedLanes;
    }

    public void setOccupiedLanes(Integer occupiedLanes) {
        this.occupiedLanes = occupiedLanes;
    }

    public Integer getTrafficFlow() {
        return trafficFlow;
    }

    public void setTrafficFlow(Integer trafficFlow) {
        this.trafficFlow = trafficFlow;
    }

    public Integer getPeopleFlow() {
        return peopleFlow;
    }

    public void setPeopleFlow(Integer peopleFlow) {
        this.peopleFlow = peopleFlow;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getRoadLevel() {
        return roadLevel;
    }

    public void setRoadLevel(String roadLevel) {
        this.roadLevel = roadLevel;
    }

    public String getRoadStatus() {
        return roadStatus;
    }

    public void setRoadStatus(String roadStatus) {
        this.roadStatus = roadStatus;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getPredictedCongestionMinutes() {
        return predictedCongestionMinutes;
    }

    public void setPredictedCongestionMinutes(Integer predictedCongestionMinutes) {
        this.predictedCongestionMinutes = predictedCongestionMinutes;
    }

    public Integer getPredictedRecoveryMinutes() {
        return predictedRecoveryMinutes;
    }

    public void setPredictedRecoveryMinutes(Integer predictedRecoveryMinutes) {
        this.predictedRecoveryMinutes = predictedRecoveryMinutes;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Long getReportUserId() {
        return reportUserId;
    }

    public void setReportUserId(Long reportUserId) {
        this.reportUserId = reportUserId;
    }
}