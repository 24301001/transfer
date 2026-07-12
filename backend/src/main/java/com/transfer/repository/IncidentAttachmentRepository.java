package com.transfer.repository;

import com.transfer.model.IncidentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, Long> {
    List<IncidentAttachment> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    List<IncidentAttachment> findByIncidentId(Long incidentId);
}
