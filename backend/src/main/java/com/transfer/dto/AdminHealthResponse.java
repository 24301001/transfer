package com.transfer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理员系统健康状态响应。
 *
 * <p>该 DTO 面向后台管理页面，除总体状态外，还提供运行环境、资源占用、
 * 依赖服务状态和业务数据概览，便于前端直接绘制状态卡片和告警信息。</p>
 */
public record AdminHealthResponse(
        String status,
        String statusMessage,
        LocalDateTime checkedAt,
        long uptimeSeconds,
        String uptime,
        ApplicationInfo application,
        ServerInfo server,
        ResourceUsage resources,
        Map<String, ComponentHealth> components,
        BusinessMetrics businessMetrics,
        List<String> warnings
) {

    public record ApplicationInfo(
            String name,
            String version,
            List<String> activeProfiles,
            String javaVersion,
            String springBootVersion,
            String timezone
    ) {
    }

    public record ServerInfo(
            String hostName,
            int port,
            int availableProcessors,
            double systemLoadAverage,
            String operatingSystem,
            String architecture
    ) {
    }

    public record ResourceUsage(
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            double heapUsagePercent,
            long nonHeapUsedBytes,
            long diskTotalBytes,
            long diskUsableBytes,
            double diskUsagePercent,
            double processCpuUsagePercent,
            double systemCpuUsagePercent
    ) {
    }

    public record ComponentHealth(
            String status,
            boolean configured,
            Long responseTimeMs,
            String message,
            Map<String, Object> details
    ) {
    }

    public record BusinessMetrics(
            long totalUsers,
            long enabledUsers,
            long disabledUsers,
            long totalIncidents,
            long activeIncidents,
            long closedIncidents,
            long totalDispatchTasks,
            long activeDispatchTasks,
            long completedDispatchTasks,
            long totalEmergencyVehicles,
            long availableEmergencyVehicles,
            long outOfServiceEmergencyVehicles
    ) {
    }
}
