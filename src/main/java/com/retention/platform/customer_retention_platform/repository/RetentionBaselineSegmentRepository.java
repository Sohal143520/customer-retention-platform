package com.retention.platform.customer_retention_platform.repository;

import com.retention.platform.customer_retention_platform.entity.RetentionBaselineSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RetentionBaselineSegmentRepository
        extends JpaRepository<RetentionBaselineSegment, Long> {
    List<RetentionBaselineSegment> findByPeriodStartAndPeriodEnd(
            LocalDate periodStart, LocalDate periodEnd);
    Optional<RetentionBaselineSegment> findTopByOrderByPeriodEndDesc();
}
