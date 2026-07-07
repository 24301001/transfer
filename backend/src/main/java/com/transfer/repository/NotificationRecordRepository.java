package com.transfer.repository;

import com.transfer.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {
}
