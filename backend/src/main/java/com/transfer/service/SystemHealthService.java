package com.transfer.service;

import com.transfer.dto.AdminHealthResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.UserStatus;
import com.transfer.enums.VehicleStatus;
import com.transfer.prediction.PredictionModelClient;
import com.transfer.recovery.RecoveryRecommendationClient;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.EmergencyVehicleRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SystemHealthService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String DEGRADED = "DEGRADED";
    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final PredictionModelClient predictionModelClient;
    private final RecoveryRecommendationClient recoveryRecommendationClient;
    private final IncidentRepository incidentRepository;
    private final UserAccountRepository userAccountRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final EmergencyVehicleRepository emergencyVehicleRepository;
    private final Environment environment;

    private final String applicationName;
    private final String applicationVersion;
    private final int serverPort;
    private final String uploadDir;

    private final String yoloBaseUrl;
    private final String yoloHealthPath;
    private final int externalHealthTimeoutMs;

    private final String siliconFlowApiUrl;
    private final String siliconFlowApiKey;
    private final String siliconFlowModel;
    private final String baiduMapServerAk;
    private final String baiduMapBrowserAk;
    private final String mailHost;
    private final String mailUsername;

    public SystemHealthService(
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            PredictionModelClient predictionModelClient,
            RecoveryRecommendationClient recoveryRecommendationClient,
            IncidentRepository incidentRepository,
            UserAccountRepository userAccountRepository,
            DispatchTaskRepository dispatchTaskRepository,
            EmergencyVehicleRepository emergencyVehicleRepository,
            Environment environment,
            @Value("${spring.application.name:traffic-risk-backend}") String applicationName,
            @Value("${app.version:0.0.1-SNAPSHOT}") String applicationVersion,
            @Value("${server.port:8080}") int serverPort,
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.yolo.base-url:}") String yoloBaseUrl,
            @Value("${app.yolo.health-path:/health}") String yoloHealthPath,
            @Value("${app.health.external-timeout-ms:2000}") int externalHealthTimeoutMs,
            @Value("${app.siliconflow.api-url:}") String siliconFlowApiUrl,
            @Value("${app.siliconflow.api-key:}") String siliconFlowApiKey,
            @Value("${app.siliconflow.model:}") String siliconFlowModel,
            @Value("${baidu.map.server-ak:}") String baiduMapServerAk,
            @Value("${baidu.map.browser-ak:}") String baiduMapBrowserAk,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.predictionModelClient = predictionModelClient;
        this.recoveryRecommendationClient = recoveryRecommendationClient;
        this.incidentRepository = incidentRepository;
        this.userAccountRepository = userAccountRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.emergencyVehicleRepository = emergencyVehicleRepository;
        this.environment = environment;
        this.applicationName = safe(applicationName);
        this.applicationVersion = safe(applicationVersion);
        this.serverPort = serverPort;
        this.uploadDir = safe(uploadDir);
        this.yoloBaseUrl = safe(yoloBaseUrl);
        this.yoloHealthPath = normalizePath(yoloHealthPath, "/health");
        this.externalHealthTimeoutMs = Math.max(500, externalHealthTimeoutMs);
        this.siliconFlowApiUrl = safe(siliconFlowApiUrl);
        this.siliconFlowApiKey = safe(siliconFlowApiKey);
        this.siliconFlowModel = safe(siliconFlowModel);
        this.baiduMapServerAk = safe(baiduMapServerAk);
        this.baiduMapBrowserAk = safe(baiduMapBrowserAk);
        this.mailHost = safe(mailHost);
        this.mailUsername = safe(mailUsername);
    }

    public AdminHealthResponse getHealth() {
        List<String> warnings = new ArrayList<>();
        Map<String, AdminHealthResponse.ComponentHealth> components = new LinkedHashMap<>();

        AdminHealthResponse.ComponentHealth database = checkDatabase();
        components.put("database", database);
        addDependencyWarning(warnings, "数据库", database, true);

        AdminHealthResponse.ComponentHealth redis = checkRedis();
        components.put("redis", redis);
        addDependencyWarning(warnings, "Redis", redis, false);

        AdminHealthResponse.ComponentHealth prediction = checkPredictionModule();
        components.put("predictionModule", prediction);
        AdminHealthResponse.ComponentHealth recovery = checkRecoveryModule();
        components.put("recoveryModule", recovery);
        addDependencyWarning(warnings, "事故预测模块", prediction, false);

        AdminHealthResponse.ComponentHealth yolo = checkYoloService();
        components.put("yoloService", yolo);
        addDependencyWarning(warnings, "YOLO 识别服务", yolo, false);

        components.put("siliconFlowAi", configurationHealth(
                isConfigured(siliconFlowApiUrl, siliconFlowApiKey, siliconFlowModel),
                "硅基流动 AI",
                mapOfNonBlank("model", siliconFlowModel)
        ));
        components.put("baiduMap", configurationHealth(
                isConfigured(baiduMapServerAk, baiduMapBrowserAk),
                "百度地图",
                Map.of("serverKeyConfigured", !baiduMapServerAk.isBlank(),
                        "browserKeyConfigured", !baiduMapBrowserAk.isBlank())
        ));
        components.put("mail", configurationHealth(
                isConfigured(mailHost, mailUsername),
                "邮件服务",
                mapOfNonBlank("host", mailHost, "account", maskEmail(mailUsername))
        ));

        AdminHealthResponse.ResourceUsage resources = collectResourceUsage();
        appendResourceWarnings(resources, warnings);

        AdminHealthResponse.BusinessMetrics businessMetrics = collectBusinessMetrics(warnings);

        String overallStatus = resolveOverallStatus(database, redis, prediction, recovery, yolo, resources);
        String statusMessage = switch (overallStatus) {
            case UP -> "系统运行正常";
            case DEGRADED -> "系统可用，但存在需要关注的异常";
            default -> "系统关键依赖不可用";
        };

        long uptimeSeconds = Math.max(0L,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);

        return new AdminHealthResponse(
                overallStatus,
                statusMessage,
                LocalDateTime.now(),
                uptimeSeconds,
                formatDuration(uptimeSeconds),
                collectApplicationInfo(),
                collectServerInfo(),
                resources,
                components,
                businessMetrics,
                List.copyOf(warnings)
        );
    }

    private AdminHealthResponse.ComponentHealth checkDatabase() {
        long startedAt = System.nanoTime();
        Map<String, Object> details = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {

            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new IllegalStateException("数据库探测语句未返回预期结果");
            }

            DatabaseMetaData metadata = connection.getMetaData();
            details.put("product", metadata.getDatabaseProductName());
            details.put("version", metadata.getDatabaseProductVersion());
            details.put("driver", metadata.getDriverName());
            details.put("readOnly", connection.isReadOnly());

            return component(UP, true, elapsedMillis(startedAt),
                    "数据库连接正常", details);
        } catch (Exception ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), details);
        }
    }

    private AdminHealthResponse.ComponentHealth checkRedis() {
        long startedAt = System.nanoTime();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            details.put("ping", pong == null ? "PONG" : pong);
            return component(UP, true, elapsedMillis(startedAt),
                    "Redis 连接正常", details);
        } catch (RedisConnectionFailureException ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), details);
        } catch (Exception ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), details);
        }
    }

    private AdminHealthResponse.ComponentHealth checkPredictionModule() {
        if (!predictionModelClient.isConfigured()) {
            return component(NOT_CONFIGURED, false, null,
                    "事故预测模块地址未配置", Map.of());
        }

        long startedAt = System.nanoTime();
        try {
            boolean healthy = predictionModelClient.healthCheck();
            return component(
                    healthy ? UP : DOWN,
                    true,
                    elapsedMillis(startedAt),
                    healthy ? "事故预测模块响应正常" : "事故预测模块不可达或健康检查失败",
                    Map.of()
            );
        } catch (Exception ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), Map.of());
        }
    }

    private AdminHealthResponse.ComponentHealth checkRecoveryModule() {
        if (!recoveryRecommendationClient.isConfigured()) {
            return component(NOT_CONFIGURED, false, null,
                    "Algorithm3 recovery service is not configured", Map.of());
        }

        long startedAt = System.nanoTime();
        try {
            boolean healthy =
                    recoveryRecommendationClient.healthCheck();

            return component(
                    healthy ? UP : DOWN,
                    true,
                    elapsedMillis(startedAt),
                    healthy
                            ? "Algorithm3 recovery service is healthy"
                            : "Algorithm3 recovery service is unavailable",
                    Map.of("port", 8003)
            );
        } catch (Exception ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), Map.of("port", 8003));
        }
    }

    private AdminHealthResponse.ComponentHealth checkYoloService() {
        if (yoloBaseUrl.isBlank()) {
            return component(NOT_CONFIGURED, false, null,
                    "YOLO 服务地址未配置", Map.of());
        }

        long startedAt = System.nanoTime();
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(externalHealthTimeoutMs);
            requestFactory.setReadTimeout(externalHealthTimeoutMs);

            RestClient client = RestClient.builder()
                    .baseUrl(trimTrailingSlash(yoloBaseUrl))
                    .requestFactory(requestFactory)
                    .build();

            client.get()
                    .uri(yoloHealthPath)
                    .retrieve()
                    .toBodilessEntity();

            return component(UP, true, elapsedMillis(startedAt),
                    "YOLO 服务响应正常",
                    Map.of("healthPath", yoloHealthPath));
        } catch (Exception ex) {
            return component(DOWN, true, elapsedMillis(startedAt),
                    conciseMessage(ex), Map.of("healthPath", yoloHealthPath));
        }
    }

    private AdminHealthResponse.ComponentHealth configurationHealth(
            boolean configured,
            String displayName,
            Map<String, Object> details
    ) {
        return component(
                configured ? UP : NOT_CONFIGURED,
                configured,
                null,
                configured ? displayName + "已配置（未执行外部连通性探测）" : displayName + "未完整配置",
                details
        );
    }

    private AdminHealthResponse.ApplicationInfo collectApplicationInfo() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = List.of("default");
        }
        return new AdminHealthResponse.ApplicationInfo(
                applicationName,
                applicationVersion,
                activeProfiles,
                System.getProperty("java.version", "unknown"),
                defaultString(SpringBootVersion.getVersion(), "unknown"),
                java.util.TimeZone.getDefault().getID()
        );
    }

    private AdminHealthResponse.ServerInfo collectServerInfo() {
        java.lang.management.OperatingSystemMXBean osBean =
                ManagementFactory.getOperatingSystemMXBean();
        return new AdminHealthResponse.ServerInfo(
                resolveHostName(),
                serverPort,
                osBean.getAvailableProcessors(),
                round2(osBean.getSystemLoadAverage()),
                osBean.getName() + " " + osBean.getVersion(),
                osBean.getArch()
        );
    }

    private AdminHealthResponse.ResourceUsage collectResourceUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

        File diskRoot = resolveDiskRoot();
        long diskTotal = Math.max(0L, diskRoot.getTotalSpace());
        long diskUsable = Math.max(0L, diskRoot.getUsableSpace());
        double diskUsagePercent = diskTotal <= 0
                ? 0.0
                : percentage(diskTotal - diskUsable, diskTotal);

        double processCpu = -1.0;
        double systemCpu = -1.0;
        java.lang.management.OperatingSystemMXBean baseBean =
                ManagementFactory.getOperatingSystemMXBean();
        if (baseBean instanceof com.sun.management.OperatingSystemMXBean extendedBean) {
            processCpu = normalizeCpuLoad(extendedBean.getProcessCpuLoad());
            systemCpu = normalizeCpuLoad(extendedBean.getCpuLoad());
        }

        return new AdminHealthResponse.ResourceUsage(
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                heap.getMax() <= 0 ? 0.0 : percentage(heap.getUsed(), heap.getMax()),
                nonHeap.getUsed(),
                diskTotal,
                diskUsable,
                diskUsagePercent,
                processCpu,
                systemCpu
        );
    }

    private AdminHealthResponse.BusinessMetrics collectBusinessMetrics(List<String> warnings) {
        try {
            long totalUsers = userAccountRepository.count();
            long enabledUsers = userAccountRepository.countByStatus(UserStatus.ENABLED);
            long disabledUsers = userAccountRepository.countByStatus(UserStatus.DISABLED);

            long totalIncidents = incidentRepository.count();
            long activeIncidents = incidentRepository.countByStatusIn(List.of(
                    IncidentStatus.REPORTED,
                    IncidentStatus.PREDICTION_REQUESTED,
                    IncidentStatus.PREDICTED,
                    IncidentStatus.DISPATCHED,
                    IncidentStatus.PROCESSING
            ));
            long closedIncidents = incidentRepository.countByStatusIn(List.of(
                    IncidentStatus.CLEARED,
                    IncidentStatus.CLOSED
            ));

            long totalDispatchTasks = dispatchTaskRepository.count();
            long activeDispatchTasks = dispatchTaskRepository.countByStatusIn(List.of(
                    TaskStatus.DISPATCHED,
                    TaskStatus.DEPARTED,
                    TaskStatus.ARRIVED,
                    TaskStatus.PROCESSING
            ));
            long completedDispatchTasks = dispatchTaskRepository.countByStatus(TaskStatus.COMPLETED);

            long totalEmergencyVehicles = emergencyVehicleRepository.count();
            long availableEmergencyVehicles = emergencyVehicleRepository.countByStatus(VehicleStatus.AVAILABLE);
            long outOfServiceEmergencyVehicles = emergencyVehicleRepository.countByStatus(VehicleStatus.OUT_OF_SERVICE);

            return new AdminHealthResponse.BusinessMetrics(
                    totalUsers,
                    enabledUsers,
                    disabledUsers,
                    totalIncidents,
                    activeIncidents,
                    closedIncidents,
                    totalDispatchTasks,
                    activeDispatchTasks,
                    completedDispatchTasks,
                    totalEmergencyVehicles,
                    availableEmergencyVehicles,
                    outOfServiceEmergencyVehicles
            );
        } catch (Exception ex) {
            warnings.add("业务统计数据读取失败：" + conciseMessage(ex));
            return new AdminHealthResponse.BusinessMetrics(
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0
            );
        }
    }

    private String resolveOverallStatus(
            AdminHealthResponse.ComponentHealth database,
            AdminHealthResponse.ComponentHealth redis,
            AdminHealthResponse.ComponentHealth prediction,
            AdminHealthResponse.ComponentHealth recovery,
            AdminHealthResponse.ComponentHealth yolo,
            AdminHealthResponse.ResourceUsage resources
    ) {
        if (DOWN.equals(database.status())) {
            return DOWN;
        }
        if (DOWN.equals(redis.status())
                || DOWN.equals(prediction.status())
                || DOWN.equals(recovery.status())
                || DOWN.equals(yolo.status())
                || resources.heapUsagePercent() >= 85.0
                || resources.diskUsagePercent() >= 90.0
                || resources.systemCpuUsagePercent() >= 90.0) {
            return DEGRADED;
        }
        return UP;
    }

    private void appendResourceWarnings(
            AdminHealthResponse.ResourceUsage resources,
            List<String> warnings
    ) {
        if (resources.heapUsagePercent() >= 85.0) {
            warnings.add("JVM 堆内存使用率较高：" + formatPercent(resources.heapUsagePercent()));
        }
        if (resources.diskUsagePercent() >= 90.0) {
            warnings.add("上传目录所在磁盘使用率较高：" + formatPercent(resources.diskUsagePercent()));
        }
        if (resources.systemCpuUsagePercent() >= 90.0) {
            warnings.add("系统 CPU 使用率较高：" + formatPercent(resources.systemCpuUsagePercent()));
        }
    }

    private void addDependencyWarning(
            List<String> warnings,
            String displayName,
            AdminHealthResponse.ComponentHealth health,
            boolean critical
    ) {
        if (DOWN.equals(health.status())) {
            warnings.add((critical ? "关键依赖" : "依赖服务")
                    + "异常：" + displayName + "，" + health.message());
        }
    }

    private File resolveDiskRoot() {
        File uploadPath = new File(uploadDir.isBlank() ? "." : uploadDir);
        File absolute = uploadPath.getAbsoluteFile();
        File root = absolute;
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }
        return root;
    }

    private AdminHealthResponse.ComponentHealth component(
            String status,
            boolean configured,
            Long responseTimeMs,
            String message,
            Map<String, Object> details
    ) {
        return new AdminHealthResponse.ComponentHealth(
                status,
                configured,
                responseTimeMs,
                defaultString(message, "无详细信息"),
                details == null ? Map.of() : Map.copyOf(details)
        );
    }

    private Map<String, Object> mapOfNonBlank(String... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = safe(pairs[i + 1]);
            if (!value.isBlank()) {
                result.put(pairs[i], value);
            }
        }
        return result;
    }

    private boolean isConfigured(String... values) {
        return Arrays.stream(values).allMatch(value -> value != null && !value.isBlank());
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }

    private double percentage(long used, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return round2((used * 100.0) / total);
    }

    private double normalizeCpuLoad(double load) {
        if (load < 0) {
            return -1.0;
        }
        return round2(load * 100.0);
    }

    private double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return -1.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        long remainingSeconds = duration.minusDays(days).minusHours(hours).minusMinutes(minutes).toSeconds();
        return days + "天 " + hours + "小时 " + minutes + "分钟 " + remainingSeconds + "秒";
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private String conciseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = safe(current.getMessage());
        if (message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private String maskEmail(String value) {
        String email = safe(value);
        int at = email.indexOf('@');
        if (at <= 1) {
            return email.isBlank() ? "" : "***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private String normalizePath(String path, String fallback) {
        String normalized = safe(path);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String trimTrailingSlash(String value) {
        String normalized = safe(value);
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
