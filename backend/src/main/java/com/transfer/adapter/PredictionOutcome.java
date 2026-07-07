package com.transfer.adapter;

import com.transfer.enums.RiskLevel;

public record PredictionOutcome(
        String accidentType,
        RiskLevel riskLevel,
        Integer congestionDurationMinutes,
        Integer recoveryDurationMinutes,
        Double confidence,
        String modelVersion,
        String suggestions
) {
}
