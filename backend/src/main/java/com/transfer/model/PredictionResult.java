package com.transfer.model;

import com.transfer.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "prediction_results")
public class PredictionResult extends AuditableEntity {

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false, length = 80)
    private String accidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskLevel riskLevel;

    private Double riskScore;

    private Integer congestionDurationMinutes;

    private Integer recoveryDurationMinutes;

    private Double confidence;

    @Column(length = 40)
    private String modelVersion;

    @Column(length = 1000)
    private String suggestions;

    @Column(length = 1500)
    private String explanation;

    @Column(length = 1000)
    private String riskFactors;

    @Column(length = 1000)
    private String imageEvidence;

    @Column(length = 1000)
    private String evidenceSummary;

    @Column(length = 80)
    private String dataModuleTraceId;

    @Column(length = 3000)
    private String rawResult;

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getAccidentType() {
        return accidentType;
    }

    public void setAccidentType(String accidentType) {
        this.accidentType = accidentType;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public Integer getCongestionDurationMinutes() {
        return congestionDurationMinutes;
    }

    public void setCongestionDurationMinutes(Integer congestionDurationMinutes) {
        this.congestionDurationMinutes = congestionDurationMinutes;
    }

    public Integer getRecoveryDurationMinutes() {
        return recoveryDurationMinutes;
    }

    public void setRecoveryDurationMinutes(Integer recoveryDurationMinutes) {
        this.recoveryDurationMinutes = recoveryDurationMinutes;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(String riskFactors) {
        this.riskFactors = riskFactors;
    }

    public String getImageEvidence() {
        return imageEvidence;
    }

    public void setImageEvidence(String imageEvidence) {
        this.imageEvidence = imageEvidence;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }

    public void setEvidenceSummary(String evidenceSummary) {
        this.evidenceSummary = evidenceSummary;
    }

    public String getDataModuleTraceId() {
        return dataModuleTraceId;
    }

    public void setDataModuleTraceId(String dataModuleTraceId) {
        this.dataModuleTraceId = dataModuleTraceId;
    }

    public String getRawResult() {
        return rawResult;
    }

    public void setRawResult(String rawResult) {
        this.rawResult = rawResult;
    }
}