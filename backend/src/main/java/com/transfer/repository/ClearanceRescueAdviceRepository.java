package com.transfer.repository;

import com.transfer.enums.AdviceReviewStatus;
import com.transfer.model.ClearanceRescueAdvice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClearanceRescueAdviceRepository
        extends JpaRepository<
        ClearanceRescueAdvice,
        Long
        > {

    /**
     * 查询事故最近生成的一条建议。
     */
    Optional<ClearanceRescueAdvice>
    findFirstByIncidentIdOrderByCreatedAtDesc(
            Long incidentId
    );

    /**
     * 查询事故最近确认的一条建议。
     *
     * 后续清障救援模块可以直接使用。
     */
    Optional<ClearanceRescueAdvice>
    findFirstByIncidentIdAndStatusOrderByConfirmedAtDesc(
            Long incidentId,
            AdviceReviewStatus status
    );
}
