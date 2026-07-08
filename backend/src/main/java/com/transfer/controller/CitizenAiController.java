package com.transfer.controller;

import com.transfer.dto.CitizenAiChatRequest;
import com.transfer.dto.CitizenAiChatResponse;
import com.transfer.dto.CitizenImmediateAdviceResponse;
import com.transfer.model.Incident;
import com.transfer.service.CitizenAiService;
import com.transfer.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report-ai")
public class CitizenAiController {

    private final CitizenAiService citizenAiService;
    private final IncidentService incidentService;

    public CitizenAiController(
            CitizenAiService citizenAiService,
            IncidentService incidentService
    ) {
        this.citizenAiService = citizenAiService;
        this.incidentService = incidentService;
    }

    /**
     * 事故上报页悬浮球AI问答。
     */
    @PostMapping("/chat")
    public CitizenAiChatResponse chat(
            @Valid @RequestBody CitizenAiChatRequest request
    ) {
        return citizenAiService.chat(request);
    }

    /**
     * 根据已上报事故重新生成普通市民即时安全提示。
     */
    @GetMapping("/incidents/{incidentId}/instant-advice")
    public CitizenImmediateAdviceResponse instantAdvice(
            @PathVariable Long incidentId
    ) {
        Incident incident = incidentService.findIncident(incidentId);
        return citizenAiService.generateImmediateAdvice(incident);
    }
}
