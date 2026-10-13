package com.retention.platform.customer_retention_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "retention_baseline_segments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"period_start", "period_end", "customer_segment"}))
public class RetentionBaselineSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "customer_segment", nullable = false, length = 30)
    private String customerSegment;

    @Column(name = "high_risk_customers", nullable = false)
    private long highRiskCustomers;

    @Column(name = "churned_within_90d", nullable = false)
    private long churnedWithin90Days;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected RetentionBaselineSegment() {
    }

    public RetentionBaselineSegment(
            LocalDate periodStart,
            LocalDate periodEnd,
            String customerSegment,
            long highRiskCustomers,
            long churnedWithin90Days,
            String importedBy,
            Instant importedAt) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.customerSegment = customerSegment;
        this.highRiskCustomers = highRiskCustomers;
        this.churnedWithin90Days = churnedWithin90Days;
        this.importedBy = importedBy;
        this.importedAt = importedAt;
    }

    public Long getId() { return id; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getCustomerSegment() { return customerSegment; }
    public long getHighRiskCustomers() { return highRiskCustomers; }
    public long getChurnedWithin90Days() { return churnedWithin90Days; }
    public String getImportedBy() { return importedBy; }
    public Instant getImportedAt() { return importedAt; }
}
