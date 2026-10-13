package com.retention.platform.customer_retention_platform.dto;

import java.time.LocalDate;
import java.util.List;

public record RetentionComparisonResponse(
        LocalDate baselineStart,
        LocalDate baselineEnd,
        LocalDate interventionStart,
        LocalDate interventionEnd,
        String status,
        boolean riskEligibilityVerified,
        String statusMessage,
        long baselineEligibleCustomers,
        long baselineChurnedCustomers,
        Long interventionCustomers,
        Long interventionChurnedCustomers,
        Double baselineChurnRatePercent,
        Double interventionChurnRatePercent,
        Double relativeReductionPercent,
        Boolean metFifteenPercentTarget,
        List<Segment> segments) {

    public record Segment(
            String customerSegment,
            long baselineEligibleCustomers,
            long baselineChurnedCustomers,
            long interventionCustomers,
            long outcomesRecorded,
            long pendingOutcomes,
            long interventionChurnedCustomers,
            Double baselineChurnRatePercent,
            Double interventionChurnRatePercent,
            Double relativeReductionPercent) {
    }
}
