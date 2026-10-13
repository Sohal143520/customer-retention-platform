package com.retention.platform.customer_retention_platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

public class PredictionRequest {

    @NotBlank
    @JsonProperty("age_group")
    private String ageGroup;

    @NotBlank
    private String region;

    @NotBlank
    @JsonProperty("customer_segment")
    private String customerSegment;

    @Min(0)
    @JsonProperty("tenure_months")
    private int tenureMonths;

    @Min(0)
    @JsonProperty("monthly_spend_inr")
    private int monthlySpendInr;

    @Min(0)
    @JsonProperty("transactions_90d")
    private int transactions90d;

    @Min(0)
    @JsonProperty("support_tickets_90d")
    private int supportTickets90d;

    @Min(0)
    @JsonProperty("avg_response_hours")
    private double avgResponseHours;

    @Min(0)
    @Max(5)
    @JsonProperty("satisfaction_score")
    private int satisfactionScore;

    @Min(0)
    @JsonProperty("app_logins_30d")
    private int appLogins30d;

    @Min(0)
    @JsonProperty("days_since_last_purchase")
    private int daysSinceLastPurchase;

    @Min(0)
    @Max(100)
    @JsonProperty("discount_usage_pct")
    private int discountUsagePct;

    @Min(0)
    @JsonProperty("payment_failures_90d")
    private int paymentFailures90d;

    public PredictionRequest() {
    }

    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCustomerSegment() { return customerSegment; }
    public void setCustomerSegment(String customerSegment) {
        this.customerSegment = customerSegment;
    }

    public int getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public int getMonthlySpendInr() { return monthlySpendInr; }
    public void setMonthlySpendInr(int monthlySpendInr) {
        this.monthlySpendInr = monthlySpendInr;
    }

    public int getTransactions90d() { return transactions90d; }
    public void setTransactions90d(int transactions90d) {
        this.transactions90d = transactions90d;
    }

    public int getSupportTickets90d() { return supportTickets90d; }
    public void setSupportTickets90d(int supportTickets90d) {
        this.supportTickets90d = supportTickets90d;
    }

    public double getAvgResponseHours() { return avgResponseHours; }
    public void setAvgResponseHours(double avgResponseHours) {
        this.avgResponseHours = avgResponseHours;
    }

    public int getSatisfactionScore() { return satisfactionScore; }
    public void setSatisfactionScore(int satisfactionScore) {
        this.satisfactionScore = satisfactionScore;
    }

    public int getAppLogins30d() { return appLogins30d; }
    public void setAppLogins30d(int appLogins30d) {
        this.appLogins30d = appLogins30d;
    }

    public int getDaysSinceLastPurchase() {
        return daysSinceLastPurchase;
    }
    public void setDaysSinceLastPurchase(int daysSinceLastPurchase) {
        this.daysSinceLastPurchase = daysSinceLastPurchase;
    }

    public int getDiscountUsagePct() { return discountUsagePct; }
    public void setDiscountUsagePct(int discountUsagePct) {
        this.discountUsagePct = discountUsagePct;
    }

    public int getPaymentFailures90d() { return paymentFailures90d; }
    public void setPaymentFailures90d(int paymentFailures90d) {
        this.paymentFailures90d = paymentFailures90d;
    }
}

