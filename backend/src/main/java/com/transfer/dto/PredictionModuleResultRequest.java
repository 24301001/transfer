package com.transfer.dto;

import com.transfer.enums.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

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
        String explanation
) {
}