package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.dto.RetentionActionRequest;
import com.retention.platform.customer_retention_platform.dto.RetentionActionResponse;
import com.retention.platform.customer_retention_platform.dto.RetentionActionStatusRequest;
import com.retention.platform.customer_retention_platform.dto.RetentionActionOutcomeRequest;
import com.retention.platform.customer_retention_platform.service.RetentionActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
public class RetentionActionController {

    private final RetentionActionService retentionActionService;

    public RetentionActionController(RetentionActionService retentionActionService) {
        this.retentionActionService = retentionActionService;
    }

    @PostMapping("/customers/{customerId}/retention-actions")
    public ResponseEntity<?> assign(
            @PathVariable String customerId,
            @RequestBody RetentionActionRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(retentionActionService.assign(
                    customerId, request, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/customers/{customerId}/retention-actions")
    public ResponseEntity<?> findForCustomer(@PathVariable String customerId) {
        try {
            List<RetentionActionResponse> actions = retentionActionService.findForCustomer(customerId);
            return ResponseEntity.ok(actions);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/retention-actions/outcome-summary")
    public ResponseEntity<?> outcomeSummary() {
        return ResponseEntity.ok(retentionActionService.outcomeSummary());
    }

    @PostMapping("/retention-actions/assign-high-risk")
    public ResponseEntity<?> assignRecommendedToUncoveredHighRiskCustomers(
            Authentication authentication) {
        int assignedCount = retentionActionService
                .assignRecommendedToUncoveredHighRiskCustomers(authentication.getName());
        return ResponseEntity.ok(Map.of("assignedCount", assignedCount));
    }

    @PatchMapping("/retention-actions/{actionId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long actionId,
            @RequestBody RetentionActionStatusRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(retentionActionService.updateStatus(
                    actionId, request == null ? null : request.status(), authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/retention-actions/{actionId}/outcome")
    public ResponseEntity<?> updateFollowUpOutcome(
            @PathVariable Long actionId,
            @RequestBody RetentionActionOutcomeRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(retentionActionService.updateFollowUpOutcome(
                    actionId, request == null ? null : request.outcome(), authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
