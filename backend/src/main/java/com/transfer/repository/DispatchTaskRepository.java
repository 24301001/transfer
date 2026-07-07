package com.transfer.repository;

import com.transfer.model.DispatchTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchTaskRepository extends JpaRepository<DispatchTask, Long> {
    List<DispatchTask> findByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId);

    List<DispatchTask> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
