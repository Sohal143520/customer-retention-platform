
from pathlib import Path
import json

import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "balanced_logistic_final.joblib"
POLICY_PATH = BASE_DIR / "screening_threshold.json"
POLICY = json.loads(POLICY_PATH.read_text(encoding="utf-8"))
SCREENING_THRESHOLD = float(POLICY["screeningThreshold"])
HIGH_RISK_THRESHOLD = float(POLICY["highRiskKpiThreshold"])

# Load the trained scikit-learn pipeline once when the API starts.
model = joblib.load(MODEL_PATH)

app = FastAPI(
    title="Customer Retention Prediction API",
    version="1.0.0",
)

class CustomerFeatures(BaseModel):
    age_group: str
    region: str
    customer_segment: str
    tenure_months: int = Field(ge=0)
    monthly_spend_inr: int = Field(ge=0)
    transactions_90d: int = Field(ge=0)
    support_tickets_90d: int = Field(ge=0)
    avg_response_hours: float = Field(ge=0)
    satisfaction_score: int = Field(ge=1, le=5)
    app_logins_30d: int = Field(ge=0)
    days_since_last_purchase: int = Field(ge=0)
    discount_usage_pct: int = Field(ge=0, le=100)
    payment_failures_90d: int = Field(ge=0)


class BulkPredictionRequest(BaseModel):
    customers: list[CustomerFeatures] = Field(max_length=5000)


def features_frame(customers: list[CustomerFeatures]) -> pd.DataFrame:
    """Convert API features to the exact names and order used during training."""
    return pd.DataFrame([{
        "Age_Group": customer.age_group,
        "Region": customer.region,
        "Customer_Segment": customer.customer_segment,
        "Tenure_Months": customer.tenure_months,
        "Monthly_Spend_INR": customer.monthly_spend_inr,
        "Transactions_90D": customer.transactions_90d,
        "Support_Tickets_90D": customer.support_tickets_90d,
        "Avg_Response_Hours": customer.avg_response_hours,
        "Satisfaction_Score": customer.satisfaction_score,
        "App_Logins_30D": customer.app_logins_30d,
        "Days_Since_Last_Purchase": customer.days_since_last_purchase,
        "Discount_Usage_Pct": customer.discount_usage_pct,
        "Payment_Failures_90D": customer.payment_failures_90d,
    } for customer in customers])


def display_feature_name(encoded_name: str) -> str:
    feature_name = encoded_name.split("__", maxsplit=1)[-1]
    category_prefixes = {
        "Age_Group_": "Age group",
        "Region_": "Region",
        "Customer_Segment_": "Customer segment",
    }
    for prefix, label in category_prefixes.items():
        if feature_name.startswith(prefix):
            return f"{label}: {feature_name[len(prefix):]}"
    return feature_name.replace("_", " ")


def explain_prediction(features: pd.DataFrame) -> dict:
    """Rank fitted Logistic Regression terms by signed log-odds contribution."""
    preprocessor = model.named_steps["preprocessor"]
    classifier = model.named_steps["classifier"]
    transformed = preprocessor.transform(features)
    row = transformed.toarray()[0] if hasattr(transformed, "toarray") else transformed[0]
    feature_names = preprocessor.get_feature_names_out()
    coefficients = classifier.coef_[0]

    contributions = []
    for name, value, coefficient in zip(feature_names, row, coefficients):
        contribution = float(value) * float(coefficient)
        if abs(contribution) < 1e-8:
            continue
        contributions.append({
            "feature": display_feature_name(str(name)),
            "log_odds_contribution": round(contribution, 3),
        })

    increasing = sorted(
        (item for item in contributions if item["log_odds_contribution"] > 0),
        key=lambda item: item["log_odds_contribution"],
        reverse=True,
    )[:3]
    reducing = sorted(
        (item for item in contributions if item["log_odds_contribution"] < 0),
        key=lambda item: item["log_odds_contribution"],
    )[:3]

    return {
        "method": "Signed contributions from the active Logistic Regression model, measured in log-odds.",
        "risk_increasing": increasing,
        "risk_reducing": reducing,
        "note": (
            "These describe how model inputs contributed to this score. They are associations, "
            "not proof that changing a feature or taking an action will cause churn to change."
        ),
    }


def prediction_result(probability: float, model_explanation: dict | None = None) -> dict:
    risk_band = (
        "High" if probability >= HIGH_RISK_THRESHOLD
        else "Medium" if probability >= 0.30
        else "Low"
    )
    result = {
        "churn_probability": round(probability, 4),
        "churn_risk_percent": round(probability * 100, 2),
        "predicted_churn": probability >= SCREENING_THRESHOLD,
        "classification_label": "High Risk" if probability >= SCREENING_THRESHOLD else "Low Risk",
        "risk_band": risk_band,
        "screening_threshold": SCREENING_THRESHOLD,
        "high_risk_threshold": HIGH_RISK_THRESHOLD,
        "targeting_capacity": 0.30,
        "note": (
            "Predicted churn uses the 0.40 screening threshold. "
            "The separate high-risk KPI uses probability >= 0.50. "
            "Top-30% targeting is a capacity policy based on ranking."
        ),
    }
    if model_explanation is not None:
        result["model_explanation"] = model_explanation
    return result


@app.get("/health")
def health():
    return {
        "status": "UP",
        "model": MODEL_PATH.name,
        "screening_threshold": SCREENING_THRESHOLD,
        "high_risk_threshold": HIGH_RISK_THRESHOLD,
    }


@app.post("/predict")
def predict(customer: CustomerFeatures):
    try:
        features = features_frame([customer])
        probability = float(model.predict_proba(features)[0][1])
        return prediction_result(probability, explain_prediction(features))

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail="Prediction failed. Check the API logs.",
        ) from exc


@app.post("/predict/bulk")
def predict_bulk(request: BulkPredictionRequest):
    try:
        if not request.customers:
            return []

        probabilities = model.predict_proba(features_frame(request.customers))[:, 1]
        return [prediction_result(float(probability)) for probability in probabilities]
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail="Bulk prediction failed. Check the API logs.",
        ) from exc
