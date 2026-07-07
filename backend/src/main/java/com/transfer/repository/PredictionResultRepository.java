package com.transfer.repository;

import com.transfer.model.PredictionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionResultRepository extends JpaRepository<PredictionResult, Long> {
    List<PredictionResult> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
