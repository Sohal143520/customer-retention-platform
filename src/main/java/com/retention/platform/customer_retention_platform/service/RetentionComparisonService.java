package com.retention.platform.customer_retention_platform.service;

import com.retention.platform.customer_retention_platform.dto.RetentionComparisonResponse;
import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import com.retention.platform.customer_retention_platform.entity.RetentionBaselineSegment;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionBaselineSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class RetentionComparisonService {

    private final RetentionBaselineSegmentRepository baselineRepository;
    private final RetentionActionRepository actionRepository;

    public RetentionComparisonService(
            RetentionBaselineSegmentRepository baselineRepository,
            RetentionActionRepository actionRepository) {
        this.baselineRepository = baselineRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional(readOnly = true)
    public RetentionComparisonResponse compareLatestBaseline(LocalDate interventionStart) {
        if (interventionStart == null) {
            throw new IllegalArgumentException("Choose the intervention cohort start date.");
        }
        LocalDate interventionEnd = interventionStart.plusDays(89);
        List<RetentionBaselineSegment> latest = baselineRepository.findTopByOrderByPeriodEndDesc()
                .map(row -> baselineRepository.findByPeriodStartAndPeriodEnd(
                        row.getPeriodStart(), row.getPeriodEnd()))
                .orElse(List.of());
        if (latest.isEmpty()) {
            throw new IllegalArgumentException("Upload a historical baseline before comparing cohorts.");
        }

        LocalDate baselineStart = latest.getFirst().getPeriodStart();
        LocalDate baselineEnd = latest.getFirst().getPeriodEnd();
        Map<String, RetentionBaselineSegment> baselineBySegment = latest.stream()
                .collect(Collectors.toMap(
                        row -> key(row.getCustomerSegment()), row -> row,
                        (left, right) -> left, TreeMap::new));

        Instant windowStart = interventionStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = interventionEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<RetentionAction> actions = actionRepository
                .findByAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtAsc(
                        windowStart, windowEnd);

        // Use the first assigned action for each customer in the selected window.
        Map<String, RetentionAction> firstActionByCustomer = new LinkedHashMap<>();
        actions.stream().sorted(Comparator.comparing(RetentionAction::getAssignedAt)
                        .thenComparing(RetentionAction::getId))
                .forEach(action -> firstActionByCustomer.putIfAbsent(action.getCustomerId(), action));

        Map<String, List<RetentionAction>> actionsBySegment = new TreeMap<>();
        firstActionByCustomer.values().forEach(action -> {
            if (action.getCustomerSegmentAtAssignment() != null) {
                actionsBySegment.computeIfAbsent(key(action.getCustomerSegmentAtAssignment()), ignored -> new ArrayList<>())
                        .add(action);
            }
        });

        boolean followUpMatured = !Instant.now().isBefore(
                interventionEnd.plusDays(90).atStartOfDay(ZoneOffset.UTC).toInstant());
        boolean riskEligibilityVerified = !firstActionByCustomer.isEmpty()
                && firstActionByCustomer.values().stream().allMatch(action ->
                        action.getChurnProbabilityAtAssignment() != null
                                && action.getChurnProbabilityAtAssignment() >= 0.50
                                && action.getChurnProbabilityAtAssignment() <= 1.0
                                && action.getRiskScoredAtAssignment() != null
                                && !action.getRiskScoredAtAssignment().isAfter(action.getAssignedAt())
                                && !action.getRiskScoredAtAssignment().isBefore(
                                        action.getAssignedAt().minusSeconds(24 * 60 * 60)));
        List<RetentionComparisonResponse.Segment> segmentResults = new ArrayList<>();
        long baselineEligible = 0;
        long baselineChurned = 0;
        long interventionEligible = 0;
        long interventionChurned = 0;
        boolean complete = followUpMatured;

        for (var entry : baselineBySegment.entrySet()) {
            RetentionBaselineSegment base = entry.getValue();
            List<RetentionAction> segmentActions = actionsBySegment.getOrDefault(entry.getKey(), List.of());
            long recorded = segmentActions.stream().filter(this::hasRecordedOutcome).count();
            long churned = segmentActions.stream()
                    .filter(action -> "CHURNED".equals(action.getFollowUpOutcome())).count();
            long pending = segmentActions.size() - recorded;
            boolean segmentComplete = followUpMatured
                    && segmentActions.size() == base.getHighRiskCustomers()
                    && pending == 0
                    && segmentActions.stream().allMatch(this::hasVerifiedHighRiskSnapshot);
            complete &= segmentComplete;

            Double baseRate = rate(base.getChurnedWithin90Days(), base.getHighRiskCustomers());
            Double interventionRate = segmentComplete
                    ? rate(churned, segmentActions.size()) : null;
            Double reduction = segmentComplete
                    ? relativeReduction(baseRate, interventionRate) : null;
            segmentResults.add(new RetentionComparisonResponse.Segment(
                    base.getCustomerSegment(), base.getHighRiskCustomers(),
                    base.getChurnedWithin90Days(), segmentActions.size(), recorded,
                    pending, churned, baseRate, interventionRate, reduction));

            baselineEligible += base.getHighRiskCustomers();
            baselineChurned += base.getChurnedWithin90Days();
            interventionEligible += segmentActions.size();
            interventionChurned += churned;
        }

        boolean hasUnexpectedSegment = actionsBySegment.keySet().stream()
                .anyMatch(segment -> !baselineBySegment.containsKey(segment));
        if (hasUnexpectedSegment) complete = false;

        Double baselineRate = rate(baselineChurned, baselineEligible);
        Double interventionRate = complete
                ? rate(interventionChurned, interventionEligible) : null;
        Double reduction = complete ? relativeReduction(baselineRate, interventionRate) : null;
        String statusMessage = complete
                ? "Complete 90-day descriptive comparison. Every action has a saved model score of at least 50% from within 24 hours before assignment. This before/after result does not prove that actions caused the change."
                : incompleteMessage(followUpMatured, hasUnexpectedSegment,
                        riskEligibilityVerified, segmentResults);

        return new RetentionComparisonResponse(
                baselineStart, baselineEnd, interventionStart, interventionEnd,
                complete ? "COMPLETE" : "INCOMPLETE", riskEligibilityVerified, statusMessage,
                baselineEligible, baselineChurned,
                complete ? interventionEligible : null,
                complete ? interventionChurned : null,
                baselineRate, interventionRate, reduction,
                reduction == null ? null : reduction >= 15.0,
                segmentResults);
    }

    private boolean hasRecordedOutcome(RetentionAction action) {
        return "RETAINED".equals(action.getFollowUpOutcome())
                || "CHURNED".equals(action.getFollowUpOutcome());
    }

    private boolean hasVerifiedHighRiskSnapshot(RetentionAction action) {
        Double probability = action.getChurnProbabilityAtAssignment();
        Instant scoredAt = action.getRiskScoredAtAssignment();
        return probability != null && probability >= 0.50 && probability <= 1.0
                && scoredAt != null && !scoredAt.isAfter(action.getAssignedAt())
                && !scoredAt.isBefore(action.getAssignedAt().minusSeconds(24 * 60 * 60));
    }

    private Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 100.0 / denominator;
    }

    private Double relativeReduction(Double baseline, Double intervention) {
        return baseline == null || baseline == 0 || intervention == null
                ? null : (baseline - intervention) * 100.0 / baseline;
    }

    private String incompleteMessage(boolean matured, boolean unexpectedSegment,
            boolean riskEligibilityVerified,
            List<RetentionComparisonResponse.Segment> segments) {
        List<String> reasons = new ArrayList<>();
        if (!matured) reasons.add("the full 90-day intervention follow-up period has not elapsed");
        if (unexpectedSegment) reasons.add("actions exist for segments missing from the baseline");
        if (!riskEligibilityVerified) reasons.add("one or more actions lack a verified model score of at least 50% captured within 24 hours before assignment");
        if (segments.stream().anyMatch(segment -> segment.interventionCustomers() != segment.baselineEligibleCustomers())) {
            reasons.add("intervention customer counts do not match baseline eligible counts in every segment");
        }
        if (segments.stream().anyMatch(segment -> segment.pendingOutcomes() > 0)) {
            reasons.add("some first-action follow-up outcomes are still pending");
        }
        if (reasons.isEmpty()) reasons.add("the cohort is not complete");
        return "Incomplete comparison: " + String.join("; ", reasons) + ". No reduction result is reported.";
    }

    private String key(String segment) {
        return segment.toLowerCase(java.util.Locale.ROOT);
    }
}
