package com.transfer.controller;

import com.transfer.dto.CreateIncidentRequest;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.PredictionDisplayResponse;
import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.dto.PredictionRequest;
import com.transfer.dto.PredictionSubmitResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;
import com.transfer.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<Incident> create(@Valid @RequestBody CreateIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(request));
    }

    @PostMapping(value = "/with-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentDetailResponse> createWithAttachments(
            @RequestPart("incident") @Valid CreateIncidentRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.createWithAttachments(request, files));
    }

    @PostMapping(value = "/{incidentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentAttachment> uploadAttachment(
            @PathVariable Long incidentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) Long uploadedBy
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.uploadAttachment(incidentId, file, uploadedBy));
    }

    @PostMapping(value = "/{incidentId}/attachments/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<IncidentAttachment>> uploadAttachments(
            @PathVariable Long incidentId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "uploadedBy", required = false) Long uploadedBy
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.uploadAttachments(incidentId, files, uploadedBy));
    }

    @GetMapping("/{incidentId}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> findAttachmentFile(
            @PathVariable Long incidentId,
            @PathVariable Long attachmentId
    ) {
        IncidentAttachment attachment = incidentService.findAttachment(incidentId, attachmentId);
        Resource resource = incidentService.loadAttachmentFile(attachment);
        MediaType mediaType = attachment.getContentType() == null || attachment.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(attachment.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }

    @PostMapping("/consequence-predictions")
    public PredictionSubmitResponse submitPredictionForCompatibility(
            @RequestBody PredictionRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId
    ) {
        return incidentService.submitPredictionRequest(request.incidentId(), operatorUserId);
    }

    @PostMapping("/{incidentId}/prediction-requests")
    public PredictionSubmitResponse submitPredictionRequest(
            @PathVariable Long incidentId,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId
    ) {
        return incidentService.submitPredictionRequest(incidentId, operatorUserId);
    }

    @PostMapping("/{incidentId}/prediction-results")
    public PredictionDisplayResponse acceptPredictionModuleResult(
            @PathVariable Long incidentId,
            @Valid @RequestBody PredictionModuleResultRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId
    ) {
        PredictionResult result = incidentService.acceptPredictionResult(incidentId, request, operatorUserId);
        return incidentService.findLatestPrediction(result.getIncidentId());
    }

    @GetMapping("/{incidentId}/prediction-result/latest")
    public PredictionDisplayResponse findLatestPrediction(@PathVariable Long incidentId) {
        return incidentService.findLatestPrediction(incidentId);
    }

    @GetMapping
    public Page<Incident> findAll(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return incidentService.findAll(status, riskLevel, keyword, pageable);
    }

    @GetMapping("/{incidentId}")
    public IncidentDetailResponse findDetail(@PathVariable Long incidentId) {
        return incidentService.findDetail(incidentId);
    }
}