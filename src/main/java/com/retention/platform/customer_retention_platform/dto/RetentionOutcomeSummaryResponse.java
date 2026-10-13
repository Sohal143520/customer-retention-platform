package com.retention.platform.customer_retention_platform.dto;

public record RetentionOutcomeSummaryResponse(
        long totalActions,
        long outcomesRecorded,
        long pendingOutcomes,
        long retained,
        long churned,
        Double observedRetainedSharePercent) {
}
