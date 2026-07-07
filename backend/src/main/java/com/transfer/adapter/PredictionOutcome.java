package com.transfer.adapter;

import com.transfer.enums.RiskLevel;

import java.util.List;

public record PredictionOutcome(
        String accidentType,
        RiskLevel riskLevel,
        Integer congestionDurationMinutes,
        Integer recoveryDurationMinutes,
        Double confidence,
        String modelVersion,
        String suggestions,
        List<String> riskFactors,
        String evidenceSummary
) {
}