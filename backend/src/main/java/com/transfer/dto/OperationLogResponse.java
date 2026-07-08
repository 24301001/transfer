package com.transfer.dto;

import com.transfer.model.OperationLog;

import java.time.LocalDateTime;

public record OperationLogResponse(
        Long id,
        Long operatorUserId,
        String operationType,
        String objectType,
        String objectId,
        String ipAddress,
        String detail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OperationLogResponse from(OperationLog log) {
        return new OperationLogResponse(
                log.getId(),
                log.getOperatorUserId(),
                log.getOperationType(),
                log.getObjectType(),
                log.getObjectId(),
                log.getIpAddress(),
                log.getDetail(),
                log.getCreatedAt(),
                log.getUpdatedAt()
        );
    }
}
