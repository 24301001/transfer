package com.transfer.repository;

import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    Optional<Incident> findByIncidentNo(String incidentNo);

    long countByStatus(IncidentStatus status);

    long countByStatusIn(Collection<IncidentStatus> statuses);

    long countByRiskLevel(RiskLevel riskLevel);
}
