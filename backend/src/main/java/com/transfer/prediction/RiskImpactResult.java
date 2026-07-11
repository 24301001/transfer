package com.transfer.prediction;

import com.transfer.enums.RiskLevel;

import java.util.List;

/**
 * 算法 B（风险影响评估）的输出结果。
 */
public record RiskImpactResult(
        RiskLevel riskLevel,
        Double riskScore,
        Integer congestionDurationMinutes,
        Integer recoveryDurationMinutes,
        Double confidence,
        String modelVersion,
        List<String> riskFactors,
        String suggestion,
        String explanation
) {
}
