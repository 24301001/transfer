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

    private Integer congestionDurationMinutes;

    private Integer recoveryDurationMinutes;

    private Double confidence;

    @Column(length = 40)
    private String modelVersion;

    @Column(length = 1000)
    private String suggestions;

    @Column(length = 1500)
    private String explanation;

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
}
