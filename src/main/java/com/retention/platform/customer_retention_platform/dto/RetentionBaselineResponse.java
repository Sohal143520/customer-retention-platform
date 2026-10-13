package com.retention.platform.customer_retention_platform.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RetentionBaselineResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant importedAt,
        String importedBy,
        long eligibleHighRiskCustomers,
        long churnedCustomers,
        Double observedChurnRatePercent,
        List<Segment> segments) {

    public record Segment(
            String customerSegment,
            long eligibleHighRiskCustomers,
            long churnedCustomers,
            double observedChurnRatePercent) {
    }
}
