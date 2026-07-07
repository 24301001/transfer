package com.transfer.repository;

import com.transfer.enums.TaskStatus;
import com.transfer.enums.TaskType;
import com.transfer.model.DispatchTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DispatchTaskRepository
        extends JpaRepository<DispatchTask, Long> {

    List<DispatchTask>
    findByReceiverUserIdOrderByCreatedAtDesc(
            Long receiverUserId
    );

    List<DispatchTask>
    findByIncidentIdOrderByCreatedAtDesc(
            Long incidentId
    );

    long countByIncidentId(Long incidentId);

    long countByIncidentIdAndStatusIn(
            Long incidentId,
            Collection<TaskStatus> statuses
    );

    boolean existsByIncidentIdAndTaskTypeAndReceiverUserIdAndStatusIn(
            Long incidentId,
            TaskType taskType,
            Long receiverUserId,
            Collection<TaskStatus> statuses
    );
}
