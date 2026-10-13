package com.retention.platform.customer_retention_platform.repository;

import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.time.Instant;
import java.util.List;

public interface RetentionActionRepository extends JpaRepository<RetentionAction, Long> {
    List<RetentionAction> findByCustomerIdOrderByAssignedAtDesc(String customerId);
    List<RetentionAction> findByCustomerIdNotIn(Collection<String> customerIds);
    List<RetentionAction> findByStatusIn(Collection<String> statuses);
    List<RetentionAction> findByAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtAsc(
            Instant start, Instant end);
    long countByFollowUpOutcome(String followUpOutcome);
    long countByFollowUpOutcomeIsNull();
}
