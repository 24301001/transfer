package com.transfer.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.transfer.dispatch.DispatchRecommendationClient;
import com.transfer.dto.HealthResponse;
import com.transfer.recovery.RecoveryRecommendationClient;

@RestController
public class HealthController {

    private final RecoveryRecommendationClient recoveryClient;
    private final DispatchRecommendationClient dispatchClient;

    public HealthController(
            RecoveryRecommendationClient recoveryClient,
            DispatchRecommendationClient dispatchClient
    ) {
        this.recoveryClient = recoveryClient;
        this.dispatchClient = dispatchClient;
    }

    @GetMapping({"/api/health", "/api/v1/health"})
    public HealthResponse health() {
        Map<String, String> deps = new LinkedHashMap<>();
        deps.put("database", "MySQL/JPA");
        deps.put("predictionClient", "algorithm2");
        deps.put("recoveryClient", recoveryClient != null && recoveryClient.isConfigured()
                ? "algorithm3-UP" : "unavailable");
        deps.put("dispatchClient", dispatchClient != null && dispatchClient.isConfigured()
                ? "algorithm4-UP" : "unavailable");
        deps.put("mapProvider", "baidu");
        return new HealthResponse("UP", LocalDateTime.now(), deps);
    }
}