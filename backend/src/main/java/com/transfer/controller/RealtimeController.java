package com.transfer.controller;

import com.transfer.service.RealtimeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private final RealtimeService realtimeService;

    public RealtimeController(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @GetMapping(value = "/road-risk/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRoadRisk() {
        return realtimeService.subscribe();
    }
}
