package com.transfer.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record HealthResponse(
        String status,
        LocalDateTime time,
        Map<String, String> dependencies
) {
}
