package com.transfer.model;

import java.time.LocalDateTime;

import com.transfer.enums.CoordinateType;
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

    /**
     * 原始坐标。
     */
    private Double longitude;

    private Double latitude;

    /**
     * 原始坐标类型。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CoordinateType coordinateType = CoordinateType.WGS84;

    /**
     * 转换后的百度地图 BD09 坐标。
     */
    private Double baiduLongitude;

    private Double baiduLatitude;

    /**
     * 百度地图返回的标准地址。
     */
    @Column(length = 255)
    private String mapFormattedAddress;

    /**
     * 百度地图返回的位置语义描述。
     */
    @Column(length = 500)
    private String mapSemanticDescription;

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

    /**
     * 涉及人数（现场人员填写）。
     */
    private Integer peopleInvolved;

    /**
     * 受伤人数（现场人员填写，AI不检测行人受伤）。
     */
    private Integer injuredCount;

    /**
     * 伤情预估描述（现场人员填写）。
     */
    @Column(length = 500)
    private String injuryEstimate;

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

    /**
     * FR-18：是否需要支援。
     */
    @Column(nullable = false)
    private Boolean supportRequired = false;

    @Column(length = 500)
    private String supportReason;

    /**
     * 是否由指挥人员手动修改过支援判断。
     * 手动修改后，自动规则不再覆盖。
     */
    @Column(nullable = false)
    private Boolean supportDecisionManual = false;

    private Long supportDecisionByUserId;

    private LocalDateTime supportDecisionAt;

    /**
     * 面向普通市民的即时提示，不作为正式调度方案。
     */
    @Column(length = 1000)
    private String citizenImmediateAdvice;

    @Column(nullable = false)
    private Boolean casualtyDetected = false;

    private Integer estimatedPoliceArrivalMinutes;

    @Column(length = 160)
    private String policeArrivalText;

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

    public String getMapFormattedAddress() {
        return mapFormattedAddress;
    }

    public void setMapFormattedAddress(String mapFormattedAddress) {
        this.mapFormattedAddress = mapFormattedAddress;
    }

    public String getMapSemanticDescription() {
        return mapSemanticDescription;
    }

    public void setMapSemanticDescription(
            String mapSemanticDescription
    ) {
        this.mapSemanticDescription = mapSemanticDescription;
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

    public void setInitialAccidentType(
            String initialAccidentType
    ) {
        this.initialAccidentType = initialAccidentType;
    }

    public String getConfirmedAccidentType() {
        return confirmedAccidentType;
    }

    public void setConfirmedAccidentType(
            String confirmedAccidentType
    ) {
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

    public void setPredictedCongestionMinutes(
            Integer predictedCongestionMinutes
    ) {
        this.predictedCongestionMinutes =
                predictedCongestionMinutes;
    }

    public Integer getPredictedRecoveryMinutes() {
        return predictedRecoveryMinutes;
    }

    public void setPredictedRecoveryMinutes(
            Integer predictedRecoveryMinutes
    ) {
        this.predictedRecoveryMinutes =
                predictedRecoveryMinutes;
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

    public Boolean getSupportRequired() {
        return supportRequired;
    }

    public void setSupportRequired(Boolean supportRequired) {
        this.supportRequired = supportRequired;
    }

    public String getSupportReason() {
        return supportReason;
    }

    public void setSupportReason(String supportReason) {
        this.supportReason = supportReason;
    }

    public Boolean getSupportDecisionManual() {
        return supportDecisionManual;
    }

    public void setSupportDecisionManual(
            Boolean supportDecisionManual
    ) {
        this.supportDecisionManual = supportDecisionManual;
    }

    public Long getSupportDecisionByUserId() {
        return supportDecisionByUserId;
    }

    public void setSupportDecisionByUserId(
            Long supportDecisionByUserId
    ) {
        this.supportDecisionByUserId =
                supportDecisionByUserId;
    }

    public LocalDateTime getSupportDecisionAt() {
        return supportDecisionAt;
    }

    public void setSupportDecisionAt(
            LocalDateTime supportDecisionAt
    ) {
        this.supportDecisionAt = supportDecisionAt;
    }

    public String getCitizenImmediateAdvice() {
        return citizenImmediateAdvice;
    }

    public void setCitizenImmediateAdvice(
            String citizenImmediateAdvice
    ) {
        this.citizenImmediateAdvice = citizenImmediateAdvice;
    }

    public Boolean getCasualtyDetected() {
        return casualtyDetected;
    }

    public void setCasualtyDetected(Boolean casualtyDetected) {
        this.casualtyDetected = casualtyDetected;
    }

    public Integer getEstimatedPoliceArrivalMinutes() {
        return estimatedPoliceArrivalMinutes;
    }

    public void setEstimatedPoliceArrivalMinutes(
            Integer estimatedPoliceArrivalMinutes
    ) {
        this.estimatedPoliceArrivalMinutes =
                estimatedPoliceArrivalMinutes;
    }

    public String getPoliceArrivalText() {
        return policeArrivalText;
    }

    public void setPoliceArrivalText(String policeArrivalText) {
        this.policeArrivalText = policeArrivalText;
    }

    public Long getReportUserId() {
        return reportUserId;
    }

    public void setReportUserId(Long reportUserId) {
        this.reportUserId = reportUserId;
    }

    public Integer getPeopleInvolved() {
        return peopleInvolved;
    }

    public void setPeopleInvolved(Integer peopleInvolved) {
        this.peopleInvolved = peopleInvolved;
    }

    public Integer getInjuredCount() {
        return injuredCount;
    }

    public void setInjuredCount(Integer injuredCount) {
        this.injuredCount = injuredCount;
    }

    public String getInjuryEstimate() {
        return injuryEstimate;
    }

    public void setInjuryEstimate(String injuryEstimate) {
        this.injuryEstimate = injuryEstimate;
    }
}