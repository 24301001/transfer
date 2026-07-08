package com.transfer.repository;

import com.transfer.model.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    long countByOperationType(String operationType);

    List<OperationLog> findTop10ByOperationTypeOrderByCreatedAtDesc(String operationType);

    List<OperationLog> findTop10ByOperationTypeInOrderByCreatedAtDesc(Collection<String> operationTypes);
}
