package com.transfer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminSystemStatusResponse(
        LocalDateTime checkedAt,
        String status,
        RuntimeInfo runtime,
        MemoryInfo memory,
        Map<String, Long> userStatusCounts,
        Map<String, Long> incidentStatusCounts,
        Map<String, Long> riskLevelCounts,
        Map<String, Long> dispatchTaskStatusCounts,
        Map<String, Long> notificationStatusCounts,
        Map<String, Long> operationLogCounts,
        List<OperationLogResponse> recentApiCalls,
        List<OperationLogResponse> recentExceptionLogs
) {
    public record RuntimeInfo(
            String javaVersion,
            String osName,
            String applicationPid,
            Long uptimeSeconds
    ) {
    }

    public record MemoryInfo(
            Long maxMb,
            Long totalMb,
            Long freeMb,
            Long usedMb
    ) {
    }
}
