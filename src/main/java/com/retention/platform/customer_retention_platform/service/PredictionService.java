
package com.retention.platform.customer_retention_platform.service;

import com.retention.platform.customer_retention_platform.dto.BulkPredictionResponse;
import com.retention.platform.customer_retention_platform.dto.PredictionRequest;
import com.retention.platform.customer_retention_platform.dto.PredictionResponse;
import com.retention.platform.customer_retention_platform.entity.Customer;
import com.retention.platform.customer_retention_platform.repository.CustomerRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Instant;

@Service
public class PredictionService {

    private static final double TARGETING_CAPACITY = 0.30;

    private final RestClient restClient;
    private final CustomerRepository customerRepository;
    private final RetentionActionRepository retentionActionRepository;

    public PredictionService(
            @Value("${prediction.api.url:http://127.0.0.1:8000}")
            String predictionApiUrl,
            CustomerRepository customerRepository,
            RetentionActionRepository retentionActionRepository) {

        this.restClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(predictionApiUrl)
                .build();

        this.customerRepository = customerRepository;
        this.retentionActionRepository = retentionActionRepository;
    }

    // Existing single-customer prediction
    public PredictionResponse predict(PredictionRequest request) {

        return restClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PredictionResponse.class);
    }

    // Bulk prediction and top-30% targeting
    public BulkPredictionResponse predictAllCustomers() {

        List<Customer> customers = customerRepository.findAll();

        List<BulkPredictionResponse.CustomerRisk> results =
                new ArrayList<>();

        // Empty database: return an empty result safely.
        if (customers.isEmpty()) {
            return new BulkPredictionResponse(
                    0, 0, TARGETING_CAPACITY, results);
        }

        Set<String> customersWithActions = retentionActionRepository
                .findByStatusIn(List.of("ASSIGNED", "CONTACTED", "COMPLETED"))
                .stream()
                .map(RetentionAction::getCustomerId)
                .collect(Collectors.toSet());

        // Send the full population in one request so inference is vectorized.
        List<PredictionRequest> requests = customers.stream()
                .map(this::toPredictionRequest)
                .toList();
        List<PredictionResponse> predictions = restClient.post()
                .uri("/predict/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("customers", requests))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (predictions == null || predictions.size() != customers.size()) {
            throw new IllegalStateException(
                    "The prediction API returned an unexpected number of results.");
        }

        for (int customerIndex = 0; customerIndex < customers.size(); customerIndex++) {
            Customer customer = customers.get(customerIndex);
            PredictionResponse prediction = predictions.get(customerIndex);

            if (prediction == null) {
                throw new IllegalStateException(
                        "The prediction API returned an empty response.");
            }

            if (!Double.isFinite(prediction.getChurnProbability())
                    || prediction.getChurnProbability() < 0.0
                    || prediction.getChurnProbability() > 1.0) {
                throw new IllegalStateException(
                        "The prediction API returned a churn probability outside 0–1.");
            }
            customer.recordPrediction(prediction.getChurnProbability(), Instant.now());

            BulkPredictionResponse.CustomerRisk customerRisk =
                    new BulkPredictionResponse.CustomerRisk(
                            customer.getCustomerId(),
                            customer.getChurned(),
                            customer.getRegion(),
                            customer.getCustomerSegment(),
                            customer.getTenureMonths(),
                            prediction.getChurnProbability(),
                            prediction.getChurnRiskPercent(),
                            prediction.isPredictedChurn(),
                            false
                    );

            customerRisk.setAgeGroup(customer.getAgeGroup());
            customerRisk.setMonthlySpendInr(customer.getMonthlySpendInr());
            customerRisk.setTransactions90d(customer.getTransactions90d());
            customerRisk.setSupportTickets90d(
                    customer.getSupportTickets90d());
            customerRisk.setAvgResponseHours(
                    customer.getAvgResponseHours());
            customerRisk.setSatisfactionScore(
                    customer.getSatisfactionScore());
            customerRisk.setAppLogins30d(customer.getAppLogins30d());
            customerRisk.setDaysSinceLastPurchase(
                    customer.getDaysSinceLastPurchase());
            customerRisk.setDiscountUsagePct(
                    customer.getDiscountUsagePct());
            customerRisk.setPaymentFailures90d(
                    customer.getPaymentFailures90d());
            customerRisk.setActionAssigned(
                    customersWithActions.contains(customer.getCustomerId()));

            results.add(customerRisk);
        }

        // Preserve the server-generated model score so action assignment can verify eligibility.
        customerRepository.saveAll(customers);

        // Highest churn probability first.
        results.sort(
                Comparator.comparingDouble(
                        BulkPredictionResponse.CustomerRisk::getChurnProbability
                ).reversed()
        );

        // Select the top 30%, rounding up for fractional customers.
        int targetedCount = (int) Math.ceil(
                results.size() * TARGETING_CAPACITY
        );

        for (int i = 0; i < targetedCount; i++) {
            results.get(i).setTargeted(true);
        }

        return new BulkPredictionResponse(
                results.size(),
                targetedCount,
                TARGETING_CAPACITY,
                results
        );
    }

    private PredictionRequest toPredictionRequest(Customer customer) {
        PredictionRequest request = new PredictionRequest();
        request.setAgeGroup(customer.getAgeGroup());
        request.setRegion(customer.getRegion());
        request.setCustomerSegment(customer.getCustomerSegment());
        request.setTenureMonths(customer.getTenureMonths());
        request.setMonthlySpendInr(customer.getMonthlySpendInr());
        request.setTransactions90d(customer.getTransactions90d());
        request.setSupportTickets90d(customer.getSupportTickets90d());
        request.setAvgResponseHours(customer.getAvgResponseHours());
        request.setSatisfactionScore(customer.getSatisfactionScore());
        request.setAppLogins30d(customer.getAppLogins30d());
        request.setDaysSinceLastPurchase(customer.getDaysSinceLastPurchase());
        request.setDiscountUsagePct(customer.getDiscountUsagePct());
        request.setPaymentFailures90d(customer.getPaymentFailures90d());
        return request;
    }
}
