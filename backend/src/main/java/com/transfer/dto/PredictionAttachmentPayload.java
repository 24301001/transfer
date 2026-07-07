package com.transfer.dto;

public record PredictionAttachmentPayload(
        Long attachmentId,
        String originalFilename,
        String contentType,
        Long fileSize,
        String filePath
) {
}