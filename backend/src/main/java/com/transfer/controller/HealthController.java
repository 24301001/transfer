package com.transfer.controller;

import com.transfer.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/api/health", "/api/v1/health"})
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                LocalDateTime.now(),
                Map.of(
                        "database", "H2/JPA",
                        "predictionClient", "fallback-rule-v1",
                        "deepSeekClient", "placeholder",
                        "mapProvider", "baidu-placeholder"
                )
        );
    }
}
