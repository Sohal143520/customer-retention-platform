
package com.retention.platform.customer_retention_platform.dto;

import java.util.List;

public class BulkPredictionResponse {

    private int totalCustomers;
    private int targetedCustomers;
    private double targetingCapacity;
    private List<CustomerRisk> customers;

    public BulkPredictionResponse() {
    }

    public BulkPredictionResponse(
            int totalCustomers,
            int targetedCustomers,
            double targetingCapacity,
            List<CustomerRisk> customers) {
        this.totalCustomers = totalCustomers;
        this.targetedCustomers = targetedCustomers;
        this.targetingCapacity = targetingCapacity;
        this.customers = customers;
    }

    public int getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(int totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public int getTargetedCustomers() {
        return targetedCustomers;
    }

    public void setTargetedCustomers(int targetedCustomers) {
        this.targetedCustomers = targetedCustomers;
    }

    public double getTargetingCapacity() {
        return targetingCapacity;
    }

    public void setTargetingCapacity(double targetingCapacity) {
        this.targetingCapacity = targetingCapacity;
    }

    public List<CustomerRisk> getCustomers() {
        return customers;
    }

    public void setCustomers(List<CustomerRisk> customers) {
        this.customers = customers;
    }

    public static class CustomerRisk {

        private String customerId;
        private String churned;
        private String region;
        private String customerSegment;
        private int tenureMonths;
        private String ageGroup;
        private int monthlySpendInr;
        private int transactions90d;
        private int supportTickets90d;
        private double avgResponseHours;
        private int satisfactionScore;
        private int appLogins30d;
        private int daysSinceLastPurchase;
        private int discountUsagePct;
        private int paymentFailures90d;
        private double churnProbability;
        private double churnRiskPercent;
        private boolean predictedChurn;
        private boolean targeted;
        private boolean actionAssigned;

        public CustomerRisk() {
        }

        public CustomerRisk(
                String customerId,
                String churned,
                String region,
                String customerSegment,
                int tenureMonths,
                double churnProbability,
                double churnRiskPercent,
                boolean predictedChurn,
                boolean targeted) {
            this.customerId = customerId;
            this.churned = churned;
            this.region = region;
            this.customerSegment = customerSegment;
            this.tenureMonths = tenureMonths;
            this.churnProbability = churnProbability;
            this.churnRiskPercent = churnRiskPercent;
            this.predictedChurn = predictedChurn;
            this.targeted = targeted;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getChurned() {
            return churned;
        }

        public void setChurned(String churned) {
            this.churned = churned;
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

        public int getTenureMonths() {
            return tenureMonths;
        }

        public void setTenureMonths(int tenureMonths) {
            this.tenureMonths = tenureMonths;
        }

        public String getAgeGroup() {
            return ageGroup;
        }

        public void setAgeGroup(String ageGroup) {
            this.ageGroup = ageGroup;
        }

        public int getMonthlySpendInr() {
            return monthlySpendInr;
        }

        public void setMonthlySpendInr(int monthlySpendInr) {
            this.monthlySpendInr = monthlySpendInr;
        }

        public int getTransactions90d() {
            return transactions90d;
        }

        public void setTransactions90d(int transactions90d) {
            this.transactions90d = transactions90d;
        }

        public int getSupportTickets90d() {
            return supportTickets90d;
        }

        public void setSupportTickets90d(int supportTickets90d) {
            this.supportTickets90d = supportTickets90d;
        }

        public double getAvgResponseHours() {
            return avgResponseHours;
        }

        public void setAvgResponseHours(double avgResponseHours) {
            this.avgResponseHours = avgResponseHours;
        }

        public int getSatisfactionScore() {
            return satisfactionScore;
        }

        public void setSatisfactionScore(int satisfactionScore) {
            this.satisfactionScore = satisfactionScore;
        }

        public int getAppLogins30d() {
            return appLogins30d;
        }

        public void setAppLogins30d(int appLogins30d) {
            this.appLogins30d = appLogins30d;
        }

        public int getDaysSinceLastPurchase() {
            return daysSinceLastPurchase;
        }

        public void setDaysSinceLastPurchase(int daysSinceLastPurchase) {
            this.daysSinceLastPurchase = daysSinceLastPurchase;
        }

        public int getDiscountUsagePct() {
            return discountUsagePct;
        }

        public void setDiscountUsagePct(int discountUsagePct) {
            this.discountUsagePct = discountUsagePct;
        }

        public int getPaymentFailures90d() {
            return paymentFailures90d;
        }

        public void setPaymentFailures90d(int paymentFailures90d) {
            this.paymentFailures90d = paymentFailures90d;
        }

        public double getChurnProbability() {
            return churnProbability;
        }

        public void setChurnProbability(double churnProbability) {
            this.churnProbability = churnProbability;
        }

        public double getChurnRiskPercent() {
            return churnRiskPercent;
        }

        public void setChurnRiskPercent(double churnRiskPercent) {
            this.churnRiskPercent = churnRiskPercent;
        }

        public boolean isPredictedChurn() {
            return predictedChurn;
        }

        public void setPredictedChurn(boolean predictedChurn) {
            this.predictedChurn = predictedChurn;
        }

        public boolean isTargeted() {
            return targeted;
        }

        public void setTargeted(boolean targeted) {
            this.targeted = targeted;
        }

        public boolean isActionAssigned() {
            return actionAssigned;
        }

        public void setActionAssigned(boolean actionAssigned) {
            this.actionAssigned = actionAssigned;
        }
    }
}
