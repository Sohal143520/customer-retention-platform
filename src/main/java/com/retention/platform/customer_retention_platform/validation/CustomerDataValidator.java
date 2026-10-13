package com.retention.platform.customer_retention_platform.validation;

import com.retention.platform.customer_retention_platform.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerDataValidator {

    public void validate(Customer customer) {

        if (customer.getCustomerId() == null ||
                customer.getCustomerId().isBlank()) {
            throw new IllegalArgumentException(
                    "Customer_ID cannot be empty");
        }

        if (customer.getAgeGroup() == null ||
                customer.getAgeGroup().isBlank()) {
            throw new IllegalArgumentException(
                    "Age_Group cannot be empty for " +
                            customer.getCustomerId());
        }

        if (customer.getRegion() == null ||
                customer.getRegion().isBlank()) {
            throw new IllegalArgumentException(
                    "Region cannot be empty for " +
                            customer.getCustomerId());
        }

        if (customer.getCustomerSegment() == null ||
                customer.getCustomerSegment().isBlank()) {
            throw new IllegalArgumentException(
                    "Customer_Segment cannot be empty for " +
                            customer.getCustomerId());
        }

        if (customer.getTenureMonths() == null ||
                customer.getTenureMonths() < 0) {
            throw new IllegalArgumentException(
                    "Tenure_Months must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getMonthlySpendInr() == null ||
                customer.getMonthlySpendInr() < 0) {
            throw new IllegalArgumentException(
                    "Monthly_Spend_INR must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getTransactions90d() == null ||
                customer.getTransactions90d() < 0) {
            throw new IllegalArgumentException(
                    "Transactions_90D must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getSupportTickets90d() == null ||
                customer.getSupportTickets90d() < 0) {
            throw new IllegalArgumentException(
                    "Support_Tickets_90D must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getAvgResponseHours() == null ||
                customer.getAvgResponseHours() < 0) {
            throw new IllegalArgumentException(
                    "Avg_Response_Hours must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getSatisfactionScore() == null ||
                customer.getSatisfactionScore() < 1 ||
                customer.getSatisfactionScore() > 5) {
            throw new IllegalArgumentException(
                    "Satisfaction_Score must be between 1 and 5 for " +
                            customer.getCustomerId());
        }

        if (customer.getAppLogins30d() == null ||
                customer.getAppLogins30d() < 0) {
            throw new IllegalArgumentException(
                    "App_Logins_30D must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getDaysSinceLastPurchase() == null ||
                customer.getDaysSinceLastPurchase() < 0) {
            throw new IllegalArgumentException(
                    "Days_Since_Last_Purchase must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getDiscountUsagePct() == null ||
                customer.getDiscountUsagePct() < 0 ||
                customer.getDiscountUsagePct() > 100) {
            throw new IllegalArgumentException(
                    "Discount_Usage_Pct must be between 0 and 100 for " +
                            customer.getCustomerId());
        }

        if (customer.getPaymentFailures90d() == null ||
                customer.getPaymentFailures90d() < 0) {
            throw new IllegalArgumentException(
                    "Payment_Failures_90D must be >= 0 for " +
                            customer.getCustomerId());
        }

        if (customer.getChurned() == null ||
                !(customer.getChurned().equals("Yes") ||
                        customer.getChurned().equals("No"))) {
            throw new IllegalArgumentException(
                    "Churned must be Yes or No for " +
                            customer.getCustomerId());
        }
    }
}
