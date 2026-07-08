package com.transfer.dto;

import com.transfer.enums.NotificationChannel;
import com.transfer.enums.NotificationStatus;
import com.transfer.model.NotificationRecord;

import java.time.LocalDateTime;

public record NotificationRecordResponse(
        Long id,
        Long receiverUserId,
        NotificationChannel channel,
        String title,
        String content,
        NotificationStatus status,
        String failureReason,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotificationRecordResponse from(NotificationRecord record) {
        return new NotificationRecordResponse(
                record.getId(),
                record.getReceiverUserId(),
                record.getChannel(),
                record.getTitle(),
                record.getContent(),
                record.getStatus(),
                record.getFailureReason(),
                record.getSentAt(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
