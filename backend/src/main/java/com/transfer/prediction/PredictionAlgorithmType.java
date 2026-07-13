package com.transfer.prediction;

/**
 * 预测模块中可调用的算法类型。
 *
 * <ul>
 *   <li>{@link #ACCIDENT_TYPE} — 事故类型识别（算法 A）</li>
 *   <li>{@link #RISK_IMPACT} — 风险影响评估（算法 B）</li>
 * </ul>
 */
public enum PredictionAlgorithmType {

    /** 算法 A：事故类型识别（文本 + 图片 → 事故类型、证据摘要） */
    ACCIDENT_TYPE,

    /** 算法 B：风险影响评估（结构化特征 → 风险等级、拥堵/恢复时长） */
    RISK_IMPACT
}
