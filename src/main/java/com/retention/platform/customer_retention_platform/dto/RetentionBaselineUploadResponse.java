package com.retention.platform.customer_retention_platform.dto;

import java.time.LocalDate;

public record RetentionBaselineUploadResponse(
        String message,
        LocalDate periodStart,
        LocalDate periodEnd,
        int segmentsLoaded,
        long eligibleHighRiskCustomers,
        long churnedCustomers) {
}
