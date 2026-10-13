package com.retention.platform.customer_retention_platform.dto;

import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import com.retention.platform.customer_retention_platform.entity.RetentionActionAudit;
import java.time.Instant;
import java.util.List;

public record RetentionActionResponse(
        Long id,
        String customerId,
        String actionName,
        String recommendation,
        String status,
        String assignedBy,
        Instant assignedAt,
        String customerSegmentAtAssignment,
        Double churnProbabilityAtAssignment,
        Double riskPercentAtAssignment,
        Instant riskScoredAtAssignment,
        Instant updatedAt,
        String followUpOutcome,
        Instant outcomeRecordedAt,
        List<AuditEntry> history) {

    public static RetentionActionResponse from(
            RetentionAction action,
            List<RetentionActionAudit> history) {
        List<AuditEntry> entries = history.stream()
                .map(event -> new AuditEntry(
                        event.getEventType(),
                        event.getDetails(),
                        event.getActor(),
                        event.getCreatedAt()))
                .toList();
        return new RetentionActionResponse(
                action.getId(), action.getCustomerId(), action.getActionName(),
                action.getRecommendation(), action.getStatus(), action.getAssignedBy(),
                action.getAssignedAt(), action.getCustomerSegmentAtAssignment(),
                action.getChurnProbabilityAtAssignment(), action.getRiskPercentAtAssignment(),
                action.getRiskScoredAtAssignment(), action.getUpdatedAt(),
                action.getFollowUpOutcome(), action.getOutcomeRecordedAt(), entries);
    }

    public record AuditEntry(String eventType, String details, String actor, Instant createdAt) {
    }
}
