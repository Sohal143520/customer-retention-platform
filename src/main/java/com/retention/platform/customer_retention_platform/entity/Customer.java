package com.retention.platform.customer_retention_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;

    @Column(name = "age_group", nullable = false, length = 20)
    private String ageGroup;

    @Column(name = "region", nullable = false, length = 30)
    private String region;

    @Column(name = "customer_segment", nullable = false, length = 30)
    private String customerSegment;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "monthly_spend_inr", nullable = false)
    private Integer monthlySpendInr;

    @Column(name = "transactions_90d", nullable = false)
    private Integer transactions90d;

    @Column(name = "support_tickets_90d", nullable = false)
    private Integer supportTickets90d;

    @Column(name = "avg_response_hours", nullable = false)
    private Double avgResponseHours;

    @Column(name = "satisfaction_score", nullable = false)
    private Integer satisfactionScore;

    @Column(name = "app_logins_30d", nullable = false)
    private Integer appLogins30d;

    @Column(name = "days_since_last_purchase", nullable = false)
    private Integer daysSinceLastPurchase;

    @Column(name = "discount_usage_pct", nullable = false)
    private Integer discountUsagePct;

    @Column(name = "payment_failures_90d", nullable = false)
    private Integer paymentFailures90d;

    @Column(name = "churned", nullable = false, length = 10)
    private String churned;

    @Column(name = "latest_churn_probability")
    private Double latestChurnProbability;

    @Column(name = "latest_prediction_at")
    private Instant latestPredictionAt;

    public Customer() {
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAgeGroup() {
        return ageGroup;
    }

    public void setAgeGroup(String ageGroup) {
        this.ageGroup = ageGroup;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCustomerSegment() {
        return customerSegment;
    }

    public void setCustomerSegment(String customerSegment) {
        this.customerSegment = customerSegment;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public Integer getMonthlySpendInr() {
        return monthlySpendInr;
    }

    public void setMonthlySpendInr(Integer monthlySpendInr) {
        this.monthlySpendInr = monthlySpendInr;
    }

    public Integer getTransactions90d() {
        return transactions90d;
    }

    public void setTransactions90d(Integer transactions90d) {
        this.transactions90d = transactions90d;
    }

    public Integer getSupportTickets90d() {
        return supportTickets90d;
    }

    public void setSupportTickets90d(Integer supportTickets90d) {
        this.supportTickets90d = supportTickets90d;
    }

    public Double getAvgResponseHours() {
        return avgResponseHours;
    }

    public void setAvgResponseHours(Double avgResponseHours) {
        this.avgResponseHours = avgResponseHours;
    }

    public Integer getSatisfactionScore() {
        return satisfactionScore;
    }

    public void setSatisfactionScore(Integer satisfactionScore) {
        this.satisfactionScore = satisfactionScore;
    }

    public Integer getAppLogins30d() {
        return appLogins30d;
    }

    public void setAppLogins30d(Integer appLogins30d) {
        this.appLogins30d = appLogins30d;
    }

    public Integer getDaysSinceLastPurchase() {
        return daysSinceLastPurchase;
    }

    public void setDaysSinceLastPurchase(Integer daysSinceLastPurchase) {
        this.daysSinceLastPurchase = daysSinceLastPurchase;
    }

    public Integer getDiscountUsagePct() {
        return discountUsagePct;
    }

    public void setDiscountUsagePct(Integer discountUsagePct) {
        this.discountUsagePct = discountUsagePct;
    }

    public Integer getPaymentFailures90d() {
        return paymentFailures90d;
    }

    public void setPaymentFailures90d(Integer paymentFailures90d) {
        this.paymentFailures90d = paymentFailures90d;
    }

    public String getChurned() {
        return churned;
    }

    public void setChurned(String churned) {
        this.churned = churned;
    }

    public Double getLatestChurnProbability() { return latestChurnProbability; }
    public Instant getLatestPredictionAt() { return latestPredictionAt; }

    public void recordPrediction(double probability, Instant predictedAt) {
        this.latestChurnProbability = probability;
        this.latestPredictionAt = predictedAt;
    }
}
