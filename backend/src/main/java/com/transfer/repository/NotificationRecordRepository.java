package com.transfer.repository;

import com.transfer.enums.NotificationStatus;
import com.transfer.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long>, JpaSpecificationExecutor<NotificationRecord> {

    long countByStatus(NotificationStatus status);
}
