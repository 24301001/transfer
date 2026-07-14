package com.transfer.recovery;

import java.util.List;

public record RecoveryRecommendationResult(
        Integer predictedRecoveryDurationMinutes,
        String recoveryLevel,
        Double longRecoveryProbability,
        Integer classificationThresholdMinutes,
        Double confidence,
        String modelVersion,
        String recommendation,
        List<String> keyFactors,
        Object metrics,
        Object features,
        String traceId
) {
}
