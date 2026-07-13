package com.transfer.prediction;

/**
 * 预测模块返回的完整响应体。
 *
 * <p>包含一次请求中两个算法的合并结果。</p>
 */
public record PredictionModuleResponse(
        String traceId,
        String status,
        PredictionModuleResponseResults results,
        String errorMessage
) {

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean hasAccidentTypeResult() {
        return results != null && results.accidentType() != null;
    }

    public boolean hasRiskImpactResult() {
        return results != null && results.riskImpact() != null;
    }

    /**
     * 嵌套的结果子对象。
     */
    public record PredictionModuleResponseResults(
            AccidentTypeResult accidentType,
            RiskImpactResult riskImpact
    ) {
    }
}
