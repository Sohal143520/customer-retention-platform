
package com.retention.platform.customer_retention_platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class PredictionResponse {

    @JsonProperty("churn_probability")
    private double churnProbability;

    @JsonProperty("churn_risk_percent")
    private double churnRiskPercent;

    @JsonProperty("predicted_churn")
    private boolean predictedChurn;

    @JsonProperty("targeting_capacity")
    private double targetingCapacity;

    private String note;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("model_explanation")
    private Map<String, Object> modelExplanation;

    public PredictionResponse() {
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

    public double getTargetingCapacity() {
        return targetingCapacity;
    }

    public void setTargetingCapacity(double targetingCapacity) {
        this.targetingCapacity = targetingCapacity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Map<String, Object> getModelExplanation() {
        return modelExplanation;
    }

    public void setModelExplanation(Map<String, Object> modelExplanation) {
        this.modelExplanation = modelExplanation;
    }
}

