package com.transfer.model;

import com.transfer.enums.AdviceReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "clearance_rescue_advices")
public class ClearanceRescueAdvice extends AuditableEntity {

    /**
     * 所属事故。
     */
    @Column(nullable = false)
    private Long incidentId;

    /**
     * 生成建议时使用的预测结果。
     */
    @Column(nullable = false)
    private Long predictionResultId;

    /**
     * 硅基流动生成的原始建议。
     */
    @Column(nullable = false, length = 4000)
    private String aiAdvice;

    /**
     * 指挥人员最终确认的建议。
     */
    @Column(length = 4000)
    private String finalAdvice;

    /**
     * DRAFT：待确认
     * CONFIRMED：已确认
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdviceReviewStatus status =
            AdviceReviewStatus.DRAFT;

    /**
     * SILICON_FLOW 或 RULE_FALLBACK。
     */
    @Column(nullable = false, length = 32)
    private String generationSource;

    /**
     * 指挥人员是否修改过 AI 原文。
     */
    private Boolean modifiedByCommand = false;

    /**
     * 确认该建议的指挥人员 ID。
     */
    private Long confirmedByUserId;

    /**
     * 确认时间。
     */
    private LocalDateTime confirmedAt;

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Long getPredictionResultId() {
        return predictionResultId;
    }

    public void setPredictionResultId(
            Long predictionResultId
    ) {
        this.predictionResultId =
                predictionResultId;
    }

    public String getAiAdvice() {
        return aiAdvice;
    }

    public void setAiAdvice(String aiAdvice) {
        this.aiAdvice = aiAdvice;
    }

    public String getFinalAdvice() {
        return finalAdvice;
    }

    public void setFinalAdvice(
            String finalAdvice
    ) {
        this.finalAdvice = finalAdvice;
    }

    public AdviceReviewStatus getStatus() {
        return status;
    }

    public void setStatus(
            AdviceReviewStatus status
    ) {
        this.status = status;
    }

    public String getGenerationSource() {
        return generationSource;
    }

    public void setGenerationSource(
            String generationSource
    ) {
        this.generationSource =
                generationSource;
    }

    public Boolean getModifiedByCommand() {
        return modifiedByCommand;
    }

    public void setModifiedByCommand(
            Boolean modifiedByCommand
    ) {
        this.modifiedByCommand =
                modifiedByCommand;
    }

    public Long getConfirmedByUserId() {
        return confirmedByUserId;
    }

    public void setConfirmedByUserId(
            Long confirmedByUserId
    ) {
        this.confirmedByUserId =
                confirmedByUserId;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(
            LocalDateTime confirmedAt
    ) {
        this.confirmedAt = confirmedAt;
    }
}
