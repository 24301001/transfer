package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CreateSystemDataRequest;
import com.transfer.dto.SystemDataResponse;
import com.transfer.dto.UpdateSystemDataRequest;
import com.transfer.enums.SystemDataCategory;
import com.transfer.model.SystemData;
import com.transfer.repository.SystemDataRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SystemDataService {

    private final SystemDataRepository systemDataRepository;
    private final OperationLogService operationLogService;

    public SystemDataService(SystemDataRepository systemDataRepository, OperationLogService operationLogService) {
        this.systemDataRepository = systemDataRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public Page<SystemDataResponse> findAll(SystemDataCategory category, Boolean enabled, String keyword, Pageable pageable) {
        return systemDataRepository.findAll(buildSpecification(category, enabled, keyword), pageable)
                .map(SystemDataResponse::from);
    }

    @Transactional(readOnly = true)
    public SystemDataResponse findById(Long id) {
        return SystemDataResponse.from(findData(id));
    }

    @Transactional
    public SystemDataResponse create(CreateSystemDataRequest request, Long operatorUserId, String ipAddress) {
        String code = normalizeRequired(request.code(), "code");
        if (systemDataRepository.existsByCategoryAndCode(request.category(), code)) {
            throw new BadRequestException("System data already exists: " + request.category() + "/" + code);
        }

        SystemData data = new SystemData();
        data.setCategory(request.category());
        data.setCode(code);
        data.setName(normalizeRequired(request.name(), "name"));
        data.setValue(normalizeOptional(request.value()));
        data.setDescription(normalizeOptional(request.description()));
        data.setEnabled(request.enabled() == null ? true : request.enabled());
        data.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        SystemData saved = systemDataRepository.save(data);
        operationLogService.record(
                operatorUserId,
                "CREATE_SYSTEM_DATA",
                "SystemData",
                saved.getId().toString(),
                ipAddress,
                saved.getCategory() + "/" + saved.getCode() + " " + saved.getName()
        );
        return SystemDataResponse.from(saved);
    }

    @Transactional
    public SystemDataResponse update(Long id, UpdateSystemDataRequest request, Long operatorUserId, String ipAddress) {
        SystemData data = findData(id);

        SystemDataCategory targetCategory = request.category() == null ? data.getCategory() : request.category();
        String targetCode = request.code() == null ? data.getCode() : normalizeRequired(request.code(), "code");
        systemDataRepository.findByCategoryAndCode(targetCategory, targetCode)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("System data already exists: " + targetCategory + "/" + targetCode);
                });

        if (request.category() != null) {
            data.setCategory(request.category());
        }
        if (request.code() != null) {
            data.setCode(targetCode);
        }
        if (request.name() != null) {
            data.setName(normalizeRequired(request.name(), "name"));
        }
        if (request.value() != null) {
            data.setValue(normalizeOptional(request.value()));
        }
        if (request.description() != null) {
            data.setDescription(normalizeOptional(request.description()));
        }
        if (request.enabled() != null) {
            data.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            data.setSortOrder(request.sortOrder());
        }

        SystemData saved = systemDataRepository.save(data);
        operationLogService.record(
                operatorUserId,
                "UPDATE_SYSTEM_DATA",
                "SystemData",
                saved.getId().toString(),
                ipAddress,
                saved.getCategory() + "/" + saved.getCode() + " " + saved.getName()
        );
        return SystemDataResponse.from(saved);
    }

    @Transactional
    public SystemDataResponse updateEnabled(Long id, Boolean enabled, Long operatorUserId, String ipAddress) {
        if (enabled == null) {
            throw new BadRequestException("enabled is required");
        }
        SystemData data = findData(id);
        data.setEnabled(enabled);
        SystemData saved = systemDataRepository.save(data);
        operationLogService.record(
                operatorUserId,
                "UPDATE_SYSTEM_DATA_STATUS",
                "SystemData",
                saved.getId().toString(),
                ipAddress,
                saved.getCategory() + "/" + saved.getCode() + ", enabled=" + saved.getEnabled()
        );
        return SystemDataResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, Long operatorUserId, String ipAddress) {
        SystemData data = findData(id);
        systemDataRepository.delete(data);
        operationLogService.record(
                operatorUserId,
                "DELETE_SYSTEM_DATA",
                "SystemData",
                id.toString(),
                ipAddress,
                data.getCategory() + "/" + data.getCode() + " " + data.getName()
        );
    }

    private SystemData findData(Long id) {
        return systemDataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("System data not found: " + id));
    }

    private Specification<SystemData> buildSpecification(SystemDataCategory category, Boolean enabled, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("value")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeRequired(String value, String fieldName) {
        if (!hasText(value)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
