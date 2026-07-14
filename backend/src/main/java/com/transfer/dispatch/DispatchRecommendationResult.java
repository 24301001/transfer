package com.transfer.dispatch;

import java.util.List;

public record DispatchRecommendationResult(
        List<DispatchItem> dispatchPlan,
        List<Double> stateVector,
        String modelVersion,
        String traceId
) {
    public record DispatchItem(
            String taskType,
            String priority,
            int recommendedUnits,
            int estimatedArrivalMinutes,
            String reasoning,
            double score
    ) {}
}
