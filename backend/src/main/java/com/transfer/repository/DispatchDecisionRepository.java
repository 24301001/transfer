package com.transfer.repository;

import com.transfer.model.DispatchDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchDecisionRepository extends JpaRepository<DispatchDecision, Long> {

    List<DispatchDecision> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<DispatchDecision> findByCommandUserIdOrderByCreatedAtDesc(Long commandUserId);

    List<DispatchDecision> findByRescueUserIdOrderByCreatedAtDesc(Long rescueUserId);

    List<DispatchDecision> findByDispatchTaskId(Long dispatchTaskId);
}
