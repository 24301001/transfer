package com.transfer.prediction;

/**
 * 预测模块 HTTP 客户端，负责向后端预测服务发送请求并解析响应。
 */
public interface PredictionModelClient {

    /**
     * 向预测模块提交预测请求，同步等待结果返回。
     *
     * @param request 预测请求体
     * @return 预测模块返回的完整响应（含两个算法结果）
     */
    PredictionModuleResponse predict(PredictionModuleRequest request);

    /**
     * 检查预测模块是否可用。
     *
     * @return true 表示预测模块健康
     */
    boolean healthCheck();
}
