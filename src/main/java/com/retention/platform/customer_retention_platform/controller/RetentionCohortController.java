package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.dto.RetentionBaselineResponse;
import com.retention.platform.customer_retention_platform.dto.RetentionBaselineUploadResponse;
import com.retention.platform.customer_retention_platform.dto.RetentionComparisonResponse;
import com.retention.platform.customer_retention_platform.service.RetentionComparisonService;
import com.retention.platform.customer_retention_platform.service.RetentionBaselineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/retention-cohorts/baseline")
public class RetentionCohortController {

    private final RetentionBaselineService baselineService;
    private final RetentionComparisonService comparisonService;

    public RetentionCohortController(
            RetentionBaselineService baselineService,
            RetentionComparisonService comparisonService) {
        this.baselineService = baselineService;
        this.comparisonService = comparisonService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            RetentionBaselineUploadResponse result =
                    baselineService.replacePeriod(file, authentication.getName());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IOException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<RetentionBaselineResponse> latest() {
        return baselineService.latest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/comparison")
    public ResponseEntity<?> compare(@RequestParam LocalDate interventionStart) {
        try {
            RetentionComparisonResponse result =
                    comparisonService.compareLatestBaseline(interventionStart);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }
}
