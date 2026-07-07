package com.transfer.service;

import com.transfer.model.OperationLog;
import com.transfer.repository.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public OperationLog record(Long operatorUserId, String operationType, String objectType, String objectId, String ipAddress, String detail) {
        OperationLog log = new OperationLog();
        log.setOperatorUserId(operatorUserId);
        log.setOperationType(operationType);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setIpAddress(ipAddress);
        log.setDetail(detail);
        return operationLogRepository.save(log);
    }

    public Page<OperationLog> findAll(Pageable pageable) {
        return operationLogRepository.findAll(pageable);
    }
}
