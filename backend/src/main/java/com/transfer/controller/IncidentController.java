package com.transfer.controller;

import com.transfer.dto.CreateIncidentRequest;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.PredictionRequest;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;
import com.transfer.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/{incidentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentAttachment> uploadAttachment(
            @PathVariable Long incidentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) Long uploadedBy
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.uploadAttachment(incidentId, file, uploadedBy));
    }

    @PostMapping("/consequence-predictions")
    public PredictionResult predict(@RequestBody PredictionRequest request) {
        return incidentService.predict(request);
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
