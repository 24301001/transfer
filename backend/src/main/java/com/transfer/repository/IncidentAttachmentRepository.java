package com.transfer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.transfer.model.IncidentAttachment;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, Long> {
    List<IncidentAttachment> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    List<IncidentAttachment> findByIncidentId(Long incidentId);
}
