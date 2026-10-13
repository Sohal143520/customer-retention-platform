package com.retention.platform.customer_retention_platform.service;

import com.retention.platform.customer_retention_platform.dto.RetentionActionRequest;
import com.retention.platform.customer_retention_platform.dto.RetentionActionResponse;
import com.retention.platform.customer_retention_platform.dto.RetentionOutcomeSummaryResponse;
import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import com.retention.platform.customer_retention_platform.entity.RetentionActionAudit;
import com.retention.platform.customer_retention_platform.repository.CustomerRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionAuditRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetentionActionService {

    private static final Set<String> ACTION_NAMES = Set.of(
            "Priority Support Concierge",
            "Billing Assistance",
            "Loyalty Tier Incentive",
            "Engagement Check-In");
    private static final Set<String> STATUSES = Set.of(
            "ASSIGNED", "CONTACTED", "COMPLETED", "CANCELLED");
    private static final Set<String> FOLLOW_UP_OUTCOMES = Set.of(
            "PENDING", "RETAINED", "CHURNED");

    private final CustomerRepository customerRepository;
    private final RetentionActionRepository actionRepository;
    private final RetentionActionAuditRepository auditRepository;

    public RetentionActionService(
            CustomerRepository customerRepository,
            RetentionActionRepository actionRepository,
            RetentionActionAuditRepository auditRepository) {
        this.customerRepository = customerRepository;
        this.actionRepository = actionRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public RetentionActionResponse assign(
            String customerId,
            RetentionActionRequest request,
            String actor) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Customer was not found."));
        String actionName = clean(request == null ? null : request.actionName(), 100, "Action name");
        String recommendation = clean(request == null ? null : request.recommendation(), 500, "Recommendation");
        if (!ACTION_NAMES.contains(actionName)) {
            throw new IllegalArgumentException("Choose one of the available retention actions.");
        }

        if (!"No".equalsIgnoreCase(customer.getChurned())) {
            throw new IllegalArgumentException(
                    "Retention actions can only be assigned to active customers who have not churned.");
        }

        Instant now = Instant.now();
        Double probability = customer.getLatestChurnProbability();
        Instant scoredAt = customer.getLatestPredictionAt();
        if (probability == null || scoredAt == null
                || scoredAt.isAfter(now)
                || scoredAt.isBefore(now.minus(24, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException(
                    "Refresh customer predictions before assigning an action; the model score is missing or older than 24 hours.");
        }
        if (!Double.isFinite(probability) || probability < 0.50 || probability > 1.0) {
            throw new IllegalArgumentException(
                    "Retention actions can only be assigned to customers with model churn risk of at least 50%.");
        }
        double riskPercent = probability * 100.0;
        RetentionAction action = actionRepository.save(new RetentionAction(
                customerId, actionName, recommendation, actor, now,
                customer.getCustomerSegment(), probability, riskPercent, scoredAt));
        auditRepository.save(new RetentionActionAudit(
                action.getId(), "ASSIGNED", "Assigned: " + actionName, actor, now));
        return view(action);
    }

    @Transactional
    public int assignRecommendedToUncoveredHighRiskCustomers(String actor) {
        Set<String> alreadyCovered = actionRepository
                .findByStatusIn(List.of("ASSIGNED", "CONTACTED", "COMPLETED"))
                .stream()
                .map(RetentionAction::getCustomerId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<com.retention.platform.customer_retention_platform.entity.Customer> eligible =
                customerRepository.findAll().stream()
                        .filter(customer -> "No".equalsIgnoreCase(customer.getChurned()))
                        .filter(customer -> !alreadyCovered.contains(customer.getCustomerId()))
                        .filter(customer -> customer.getLatestChurnProbability() != null
                                && customer.getLatestChurnProbability() >= 0.50
                                && customer.getLatestChurnProbability() <= 1.0)
                        .filter(customer -> customer.getLatestPredictionAt() != null
                                && !customer.getLatestPredictionAt().isAfter(now)
                                && !customer.getLatestPredictionAt().isBefore(
                                        now.minus(24, ChronoUnit.HOURS)))
                        .toList();

        for (var customer : eligible) {
            RetentionActionRequest recommendation = recommendationFor(customer);
            assign(customer.getCustomerId(), recommendation, actor);
        }
        return eligible.size();
    }

    private RetentionActionRequest recommendationFor(
            com.retention.platform.customer_retention_platform.entity.Customer customer) {
        String actionName;
        String recommendation;
        if (customer.getPaymentFailures90d() >= 2) {
            actionName = "Billing Assistance";
            recommendation = "Review recent payment failures with the customer and offer guided billing support.";
        } else if (customer.getSupportTickets90d() >= 3 || customer.getSatisfactionScore() <= 2) {
            actionName = "Priority Support Concierge";
            recommendation = "Review recent support history and offer a concierge follow-up on unresolved issues.";
        } else if (customer.getTenureMonths() <= 12) {
            actionName = "Engagement Check-In";
            recommendation = "Offer an onboarding check-in and ask what would make the service more useful.";
        } else if (customer.getDiscountUsagePct() >= 60) {
            actionName = "Loyalty Tier Incentive";
            recommendation = "Review loyalty eligibility and explain available tier benefits for human approval.";
        } else {
            actionName = "Engagement Check-In";
            recommendation = "Offer a short engagement check-in and ask about the customer’s current priorities.";
        }
        return new RetentionActionRequest(actionName, recommendation);
    }

    @Transactional(readOnly = true)
    public List<RetentionActionResponse> findForCustomer(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new NoSuchElementException("Customer was not found.");
        }
        return actionRepository.findByCustomerIdOrderByAssignedAtDesc(customerId)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RetentionOutcomeSummaryResponse outcomeSummary() {
        long totalActions = actionRepository.count();
        long retained = actionRepository.countByFollowUpOutcome("RETAINED");
        long churned = actionRepository.countByFollowUpOutcome("CHURNED");
        long pending = actionRepository.countByFollowUpOutcome("PENDING")
                + actionRepository.countByFollowUpOutcomeIsNull();
        long recorded = retained + churned;
        Double retainedShare = recorded == 0
                ? null
                : (retained * 100.0) / recorded;
        return new RetentionOutcomeSummaryResponse(
                totalActions, recorded, pending, retained, churned, retainedShare);
    }

    @Transactional
    public RetentionActionResponse updateStatus(Long actionId, String requestedStatus, String actor) {
        String status = clean(requestedStatus, 20, "Status").toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status must be Assigned, Contacted, Completed, or Cancelled.");
        }

        RetentionAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new NoSuchElementException("Retention action was not found."));
        String previousStatus = action.getStatus();
        Instant now = Instant.now();
        action.updateStatus(status, now);
        actionRepository.save(action);
        auditRepository.save(new RetentionActionAudit(
                actionId, "STATUS_CHANGED", previousStatus + " → " + status, actor, now));
        return view(action);
    }

    @Transactional
    public RetentionActionResponse updateFollowUpOutcome(
            Long actionId, String requestedOutcome, String actor) {
        String outcome = clean(requestedOutcome, 20, "Follow-up outcome").toUpperCase();
        if (!FOLLOW_UP_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException(
                    "Outcome must be Pending, Retained, or Churned.");
        }

        RetentionAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new NoSuchElementException("Retention action was not found."));
        Instant now = Instant.now();
        if ("RETAINED".equals(outcome) && !action.isPastNinetyDayFollowUp(now)) {
            throw new IllegalArgumentException(
                    "Record Retained only after the 90-day follow-up period is complete.");
        }

        String previousOutcome = action.getFollowUpOutcome();
        action.updateFollowUpOutcome(outcome, now);
        actionRepository.save(action);
        auditRepository.save(new RetentionActionAudit(
                actionId, "FOLLOW_UP_OUTCOME", previousOutcome + " → " + outcome, actor, now));
        return view(action);
    }

    private RetentionActionResponse view(RetentionAction action) {
        return RetentionActionResponse.from(action,
                auditRepository.findByActionIdOrderByCreatedAtAsc(action.getId()));
    }

    private String clean(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return cleaned;
    }
}
