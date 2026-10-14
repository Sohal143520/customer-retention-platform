
package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.dto.BulkPredictionResponse;
import com.retention.platform.customer_retention_platform.dto.PredictionRequest;
import com.retention.platform.customer_retention_platform.dto.PredictionResponse;
import com.retention.platform.customer_retention_platform.service.PredictionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    private static final Logger log = LoggerFactory.getLogger(PredictionController.class);

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.info("Test endpoint reached");
        return ResponseEntity.ok("Predictions endpoint working");
    }

    // Predict churn for one customer
    @PostMapping("/churn")
    public ResponseEntity<PredictionResponse> predictChurn(
            @Valid @RequestBody PredictionRequest request) {

        log.debug("Received prediction request for customer");
        PredictionResponse response = predictionService.predict(request);
        log.debug("Prediction completed successfully");
        return ResponseEntity.ok(response);
    }

    // Predict all customers and select the highest-risk 30%
    @PostMapping("/bulk")
    public ResponseEntity<BulkPredictionResponse> predictAllCustomers() {

        log.debug("Received bulk prediction request");
        BulkPredictionResponse response =
                predictionService.predictAllCustomers();

        return ResponseEntity.ok(response);
    }
}
