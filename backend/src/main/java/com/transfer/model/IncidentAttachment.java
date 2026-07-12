package com.transfer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_attachments")
public class IncidentAttachment extends AuditableEntity {

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalFilename;

    @Column(length = 80)
    private String contentType;

    /**
     * PHOTO / VIDEO / OTHER。
     */
    @Column(nullable = false, length = 20)
    private String attachmentType = "OTHER";

    @Column(nullable = false, length = 500)
    private String filePath;

    private Long fileSize;

    private Long uploadedBy;

    @Column(nullable = false, length = 32)
    private String recognitionStatus = "PENDING";

    /**
     * AI检测到的事故类型（逗号分隔，如 "car crash,fire"），排除 "car"。
     */
    @Column(length = 200)
    private String aiDetectedTypes;

    /**
     * YOLOv5 完整检测结果 JSON（含 bbox、confidence 等）。
     */
    @Column(columnDefinition = "LONGTEXT")
    private String aiDetectionJson;

    /**
     * 指挥中心是否已查看。
     */
    @Column(nullable = false)
    private Boolean reviewed = false;

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getRecognitionStatus() {
        return recognitionStatus;
    }

    public void setRecognitionStatus(String recognitionStatus) {
        this.recognitionStatus = recognitionStatus;
    }

    public String getAiDetectedTypes() {
        return aiDetectedTypes;
    }

    public void setAiDetectedTypes(String aiDetectedTypes) {
        this.aiDetectedTypes = aiDetectedTypes;
    }

    public String getAiDetectionJson() {
        return aiDetectionJson;
    }

    public void setAiDetectionJson(String aiDetectionJson) {
        this.aiDetectionJson = aiDetectionJson;
    }

    public Boolean getReviewed() {
        return reviewed;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }
}
