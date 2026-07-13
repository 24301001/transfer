package com.transfer.service;

import com.transfer.dto.OperationLogResponse;
import com.transfer.model.OperationLog;
import com.transfer.repository.OperationLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public OperationLog record(Long operatorUserId, String operationType, String objectType, String objectId, String ipAddress, String detail) {
        OperationLog log = new OperationLog();
        log.setOperatorUserId(operatorUserId);
        log.setOperationType(operationType);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setIpAddress(ipAddress);
        log.setDetail(trimToLength(detail, 1000));
        return operationLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<OperationLogResponse> findAll(Pageable pageable) {
        return operationLogRepository.findAll(pageable).map(OperationLogResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OperationLogResponse> findByFilters(
            Long operatorUserId,
            String operationType,
            String objectType,
            String objectId,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    ) {
        return operationLogRepository.findAll(
                buildSpecification(operatorUserId, operationType, objectType, objectId, keyword, startTime, endTime),
                pageable
        ).map(OperationLogResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OperationLogResponse> findRecentByOperationType(String operationType) {
        return operationLogRepository.findTop10ByOperationTypeOrderByCreatedAtDesc(operationType)
                .stream()
                .map(OperationLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationLogResponse> findRecentByOperationTypes(Collection<String> operationTypes) {
        return operationLogRepository.findTop10ByOperationTypeInOrderByCreatedAtDesc(operationTypes)
                .stream()
                .map(OperationLogResponse::from)
                .toList();
    }

    private Specification<OperationLog> buildSpecification(
            Long operatorUserId,
            String operationType,
            String objectType,
            String objectId,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (operatorUserId != null) {
                predicates.add(cb.equal(root.get("operatorUserId"), operatorUserId));
            }
            if (hasText(operationType)) {
                predicates.add(cb.equal(root.get("operationType"), operationType.trim()));
            }
            if (hasText(objectType)) {
                predicates.add(cb.equal(root.get("objectType"), objectType.trim()));
            }
            if (hasText(objectId)) {
                predicates.add(cb.equal(root.get("objectId"), objectId.trim()));
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
                        cb.like(cb.lower(root.get("operationType")), like),
                        cb.like(cb.lower(root.get("objectType")), like),
                        cb.like(cb.lower(root.get("objectId")), like),
                        cb.like(cb.lower(root.get("detail")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
