package com.transfer.dto;

import com.transfer.model.IncidentAttachment;

import java.time.LocalDateTime;

/**
 * 指挥中心 AI 检测附件响应。
 *
 * <p>annotatedFileUrl 和 playbackUrl 均指向后端媒体代理地址，
 * 避免前端直接访问仅后端可见的 YOLO 服务地址。</p>
 */
public record AiAttachmentResponse(
        Long id,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long incidentId,
        String fileName,
        String originalFilename,
        String contentType,
        String sourceContentType,
        String attachmentType,
        String filePath,
        Long fileSize,
        Long uploadedBy,
        String recognitionStatus,
        String aiDetectedTypes,
        String aiDetectionJson,
        String annotatedFileUrl,
        String playbackUrl,
        Boolean reviewed
) {
    public static AiAttachmentResponse from(
            IncidentAttachment attachment,
            String playbackUrl
    ) {
        return new AiAttachmentResponse(
                attachment.getId(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt(),
                attachment.getIncidentId(),
                attachment.getFileName(),
                attachment.getOriginalFilename(),
                resolvePlaybackContentType(attachment),
                attachment.getContentType(),
                attachment.getAttachmentType(),
                attachment.getFilePath(),
                attachment.getFileSize(),
                attachment.getUploadedBy(),
                attachment.getRecognitionStatus(),
                attachment.getAiDetectedTypes(),
                attachment.getAiDetectionJson(),
                playbackUrl,
                playbackUrl,
                attachment.getReviewed()
        );
    }

    private static String resolvePlaybackContentType(
            IncidentAttachment attachment
    ) {
        String annotatedUrl = attachment.getAnnotatedFileUrl() == null
                ? ""
                : attachment.getAnnotatedFileUrl().toLowerCase();

        int queryIndex = annotatedUrl.indexOf('?');
        if (queryIndex >= 0) {
            annotatedUrl = annotatedUrl.substring(0, queryIndex);
        }

        if (annotatedUrl.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (annotatedUrl.endsWith(".webm")) {
            return "video/webm";
        }
        if (annotatedUrl.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (annotatedUrl.endsWith(".jpg")
                || annotatedUrl.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (annotatedUrl.endsWith(".png")) {
            return "image/png";
        }

        return attachment.getContentType();
    }
}
