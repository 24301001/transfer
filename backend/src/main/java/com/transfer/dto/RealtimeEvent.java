package com.transfer.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record RealtimeEvent(
        String type,
        LocalDateTime time,
        Map<String, Object> data
) {
}
