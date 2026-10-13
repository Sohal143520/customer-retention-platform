package com.retention.platform.customer_retention_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "retention_actions")
public class RetentionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @Column(nullable = false, length = 500)
    private String recommendation;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "customer_segment_at_assignment", length = 30)
    private String customerSegmentAtAssignment;

    @Column(name = "churn_probability_at_assignment")
    private Double churnProbabilityAtAssignment;

    @Column(name = "risk_percent_at_assignment")
    private Double riskPercentAtAssignment;

    @Column(name = "risk_scored_at_assignment")
    private Instant riskScoredAtAssignment;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Nullable so Hibernate can add this column safely to existing action rows.
    @Column(name = "follow_up_outcome", length = 20)
    private String followUpOutcome;

    @Column(name = "outcome_recorded_at")
    private Instant outcomeRecordedAt;

    protected RetentionAction() {
    }

    public RetentionAction(
            String customerId,
            String actionName,
            String recommendation,
            String assignedBy,
            Instant now) {
        this(customerId, actionName, recommendation, assignedBy, now, null);
    }

    public RetentionAction(
            String customerId,
            String actionName,
            String recommendation,
            String assignedBy,
            Instant now,
            String customerSegmentAtAssignment) {
        this(customerId, actionName, recommendation, assignedBy, now,
                customerSegmentAtAssignment, null, null, null);
    }

    public RetentionAction(
            String customerId,
            String actionName,
            String recommendation,
            String assignedBy,
            Instant now,
            String customerSegmentAtAssignment,
            Double churnProbabilityAtAssignment,
            Double riskPercentAtAssignment,
            Instant riskScoredAtAssignment) {
        this.customerId = customerId;
        this.actionName = actionName;
        this.recommendation = recommendation;
        this.status = "ASSIGNED";
        this.assignedBy = assignedBy;
        this.assignedAt = now;
        this.updatedAt = now;
        this.customerSegmentAtAssignment = customerSegmentAtAssignment;
        this.churnProbabilityAtAssignment = churnProbabilityAtAssignment;
        this.riskPercentAtAssignment = riskPercentAtAssignment;
        this.riskScoredAtAssignment = riskScoredAtAssignment;
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getActionName() { return actionName; }
    public String getRecommendation() { return recommendation; }
    public String getStatus() { return status; }
    public String getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public String getCustomerSegmentAtAssignment() { return customerSegmentAtAssignment; }
    public Double getChurnProbabilityAtAssignment() { return churnProbabilityAtAssignment; }
    public Double getRiskPercentAtAssignment() { return riskPercentAtAssignment; }
    public Instant getRiskScoredAtAssignment() { return riskScoredAtAssignment; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getFollowUpOutcome() {
        return followUpOutcome == null ? "PENDING" : followUpOutcome;
    }
    public Instant getOutcomeRecordedAt() { return outcomeRecordedAt; }

    public void updateStatus(String status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void updateFollowUpOutcome(String outcome, Instant recordedAt) {
        this.followUpOutcome = outcome;
        this.outcomeRecordedAt = "PENDING".equals(outcome) ? null : recordedAt;
        this.updatedAt = recordedAt;
    }

    public boolean isPastNinetyDayFollowUp(Instant now) {
        return !now.isBefore(assignedAt.plus(90, ChronoUnit.DAYS));
    }
}
