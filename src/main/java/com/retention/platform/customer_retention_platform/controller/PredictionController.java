
package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.dto.BulkPredictionResponse;
import com.retention.platform.customer_retention_platform.dto.PredictionRequest;
import com.retention.platform.customer_retention_platform.dto.PredictionResponse;
import com.retention.platform.customer_retention_platform.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    // Predict churn for one customer
    @PostMapping("/churn")
    public ResponseEntity<PredictionResponse> predictChurn(
            @Valid @RequestBody PredictionRequest request) {

        PredictionResponse response = predictionService.predict(request);
        return ResponseEntity.ok(response);
    }

    // Predict all customers and select the highest-risk 30%
    @PostMapping("/bulk")
    public ResponseEntity<BulkPredictionResponse> predictAllCustomers() {

        BulkPredictionResponse response =
                predictionService.predictAllCustomers();

        return ResponseEntity.ok(response);
    }
}
