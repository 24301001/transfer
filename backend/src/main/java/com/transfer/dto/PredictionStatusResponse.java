package com.transfer.dto;

/**
 * 市民匿名查询预测状态的响应体。
 */
public record PredictionStatusResponse(
        String status,
        Boolean completed,
        PredictionDisplayResponse result,
        String message
) {
}
