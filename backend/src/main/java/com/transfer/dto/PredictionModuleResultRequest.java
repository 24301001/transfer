package com.transfer.dto;

import java.util.List;

import com.transfer.enums.RiskLevel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PredictionModuleResultRequest(
        @NotBlank String accidentType,
        @NotNull RiskLevel riskLevel,
        @DecimalMin("0.0") @DecimalMax("100.0") Double riskScore,
        @NotNull @Min(0) Integer congestionDurationMinutes,
        @NotNull @Min(0) Integer recoveryDurationMinutes,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        String modelVersion,
        List<String> riskFactors,
        List<String> imageEvidence,
        String evidenceSummary,
        String dataModuleTraceId,
        String rawResult,
        String suggestion,
        String explanation,
        String recoveryRecommendation,
        Double recoveryConfidence,
        String recoveryLevel,
        String recoveryModelVersion,
        String recoveryTraceId,
        List<String> recoveryKeyFactors,
        String dispatchPlan,
        String dispatchModelVersion,
        String dispatchTraceId
) {
    public PredictionModuleResultRequest(
            String accidentType,
            RiskLevel riskLevel,
            Double riskScore,
            Integer congestionDurationMinutes,
            Integer recoveryDurationMinutes,
            Double confidence,
            String modelVersion,
            List<String> riskFactors,
            List<String> imageEvidence,
            String evidenceSummary,
            String dataModuleTraceId,
            String rawResult,
            String suggestion,
            String explanation
    ) {
        this(
                accidentType,
                riskLevel,
                riskScore,
                congestionDurationMinutes,
                recoveryDurationMinutes,
                confidence,
                modelVersion,
                riskFactors,
                imageEvidence,
                evidenceSummary,
                dataModuleTraceId,
                rawResult,
                suggestion,
                explanation,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
