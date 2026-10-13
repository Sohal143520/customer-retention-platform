package com.retention.platform.customer_retention_platform.repository;

import com.retention.platform.customer_retention_platform.entity.RetentionActionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;

public interface RetentionActionAuditRepository extends JpaRepository<RetentionActionAudit, Long> {
    List<RetentionActionAudit> findByActionIdOrderByCreatedAtAsc(Long actionId);
    void deleteByActionIdIn(Collection<Long> actionIds);
}
