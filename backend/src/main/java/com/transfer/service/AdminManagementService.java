package com.transfer.service;

import com.transfer.dto.AdminSystemStatusResponse;
import com.transfer.dto.IncidentHistoryResponse;
import com.transfer.dto.NotificationRecordResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.NotificationStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.UserStatus;
import com.transfer.model.Incident;
import com.transfer.model.PredictionResult;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.NotificationRecordRepository;
import com.transfer.repository.OperationLogRepository;
import com.transfer.repository.PredictionResultRepository;
import com.transfer.repository.UserAccountRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminManagementService {

    private final IncidentRepository incidentRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperationLogService operationLogService;
    public AdminManagementService(
            IncidentRepository incidentRepository,
            PredictionResultRepository predictionResultRepository,
            DispatchTaskRepository dispatchTaskRepository,
            UserAccountRepository userAccountRepository,
            NotificationRecordRepository notificationRecordRepository,
            OperationLogRepository operationLogRepository,
            OperationLogService operationLogService
    ) {
        this.incidentRepository = incidentRepository;
        this.predictionResultRepository = predictionResultRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationRecordRepository = notificationRecordRepository;
        this.operationLogRepository = operationLogRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public Page<IncidentHistoryResponse> findIncidentHistory(
            IncidentStatus status,
            RiskLevel riskLevel,
            String accidentType,
            String roadName,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    ) {
        return incidentRepository.findAll(
                buildIncidentSpecification(status, riskLevel, accidentType, roadName, keyword, startTime, endTime),
                pageable
        ).map(this::toIncidentHistoryResponse);
    }



    @Transactional(readOnly = true)
    public Page<NotificationRecordResponse> findNotificationRecords(
            Long receiverUserId,
            NotificationChannel channel,
            NotificationStatus status,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    ) {
        return notificationRecordRepository.findAll(
                buildNotificationSpecification(receiverUserId, channel, status, keyword, startTime, endTime),
                pageable
        ).map(NotificationRecordResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminSystemStatusResponse findSystemStatus() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        long totalMb = toMb(runtime.totalMemory());
        long freeMb = toMb(runtime.freeMemory());
        long maxMb = toMb(runtime.maxMemory());
        long usedMb = totalMb - freeMb;

        return new AdminSystemStatusResponse(
                LocalDateTime.now(),
                "UP",
                new AdminSystemStatusResponse.RuntimeInfo(
                        System.getProperty("java.version"),
                        System.getProperty("os.name"),
                        runtimeBean.getName(),
                        runtimeBean.getUptime() / 1000
                ),
                new AdminSystemStatusResponse.MemoryInfo(
                        maxMb,
                        totalMb,
                        freeMb,
                        usedMb
                ),
                buildUserStatusCounts(),
                buildIncidentStatusCounts(),
                buildRiskLevelCounts(),
                buildDispatchTaskStatusCounts(),
                buildNotificationStatusCounts(),
                buildOperationLogCounts(),
                operationLogService.findRecentByOperationType("API_CALL"),
                operationLogService.findRecentByOperationType("EXCEPTION")
        );
    }

    private IncidentHistoryResponse toIncidentHistoryResponse(Incident incident) {
        PredictionResult latestPrediction = predictionResultRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(incident.getId())
                .orElse(null);
        long dispatchTaskCount = dispatchTaskRepository.countByIncidentId(incident.getId());
        return IncidentHistoryResponse.from(incident, latestPrediction, dispatchTaskCount);
    }

    private Specification<Incident> buildIncidentSpecification(
            IncidentStatus status,
            RiskLevel riskLevel,
            String accidentType,
            String roadName,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (hasText(accidentType)) {
                String like = "%" + accidentType.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("initialAccidentType")), like),
                        cb.like(cb.lower(root.get("confirmedAccidentType")), like)
                ));
            }
            if (hasText(roadName)) {
                predicates.add(cb.like(cb.lower(root.get("roadName")), "%" + roadName.trim().toLowerCase() + "%"));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            if (hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("incidentNo")), like),
                        cb.like(cb.lower(root.get("locationName")), like),
                        cb.like(cb.lower(root.get("address")), like),
                        cb.like(cb.lower(root.get("roadName")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("initialAccidentType")), like),
                        cb.like(cb.lower(root.get("confirmedAccidentType")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }



    private Specification<com.transfer.model.NotificationRecord> buildNotificationSpecification(
            Long receiverUserId,
            NotificationChannel channel,
            NotificationStatus status,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (receiverUserId != null) {
                predicates.add(cb.equal(root.get("receiverUserId"), receiverUserId));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            if (hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("content")), like),
                        cb.like(cb.lower(root.get("failureReason")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<String, Long> buildUserStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UserStatus status : UserStatus.values()) {
            counts.put(status.name(), userAccountRepository.countByStatus(status));
        }
        counts.put("TOTAL", userAccountRepository.count());
        return counts;
    }

    private Map<String, Long> buildIncidentStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            counts.put(status.name(), incidentRepository.countByStatus(status));
        }
        counts.put("TOTAL", incidentRepository.count());
        return counts;
    }

    private Map<String, Long> buildRiskLevelCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (RiskLevel riskLevel : RiskLevel.values()) {
            counts.put(riskLevel.name(), incidentRepository.countByRiskLevel(riskLevel));
        }
        return counts;
    }

    private Map<String, Long> buildDispatchTaskStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status.name(), dispatchTaskRepository.countByStatus(status));
        }
        counts.put("TOTAL", dispatchTaskRepository.count());
        return counts;
    }

    private Map<String, Long> buildNotificationStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            counts.put(status.name(), notificationRecordRepository.countByStatus(status));
        }
        counts.put("TOTAL", notificationRecordRepository.count());
        return counts;
    }

    private Map<String, Long> buildOperationLogCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", operationLogRepository.count());
        counts.put("API_CALL", operationLogRepository.countByOperationType("API_CALL"));
        counts.put("EXCEPTION", operationLogRepository.countByOperationType("EXCEPTION"));
        counts.put("CREATE_USER", operationLogRepository.countByOperationType("CREATE_USER"));
        counts.put("UPDATE_USER", operationLogRepository.countByOperationType("UPDATE_USER"));
        counts.put("CREATE_SYSTEM_DATA", operationLogRepository.countByOperationType("CREATE_SYSTEM_DATA"));
        return counts;
    }

    private long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
