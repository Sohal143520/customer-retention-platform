"""Train the baseline balanced Logistic Regression pipeline.

Uses the baseline's stratified 80/20 split and fixed 0.40 screening cutoff.
The holdout is evaluated once and is not used to choose a threshold.
"""

from pathlib import Path
import json

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler


import os
BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
# Allow override via env var for containerized training
DATA_PATH = Path(os.getenv("TRAIN_DATA_PATH", PROJECT_DIR / "dataset" / "Client Data Set for Student Project Workbook.xlsx"))
MODEL_PATH = BASE_DIR / "balanced_logistic_final.joblib"
POLICY_PATH = BASE_DIR / "screening_threshold.json"
METRICS_PATH = BASE_DIR / "baseline_holdout_metrics.json"
RANDOM_STATE = 42
SCREENING_THRESHOLD = 0.40
HIGH_RISK_THRESHOLD = 0.50


def main():
    df = pd.read_excel(DATA_PATH)
    required = {"Customer_ID", "Churned"}
    if not required.issubset(df.columns):
        raise ValueError("Dataset must include Customer_ID and Churned columns.")
    if df["Churned"].isna().any() or not set(df["Churned"].unique()).issubset({"Yes", "No"}):
        raise ValueError("Churned must contain non-null Yes/No labels.")

    X = df.drop(columns=["Customer_ID", "Churned"])
    y = (df["Churned"] == "Yes").astype(int)
    categorical = X.select_dtypes(include=["object", "category", "string"]).columns.tolist()
    numeric = X.select_dtypes(include=["number"]).columns.tolist()
    if len(categorical) + len(numeric) != X.shape[1]:
        raise ValueError("Dataset includes unsupported feature types.")

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=RANDOM_STATE, stratify=y
    )

    preprocessor = ColumnTransformer([
        ("numeric", Pipeline([
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]), numeric),
        ("categorical", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("encoder", OneHotEncoder(drop="first", handle_unknown="ignore")),
        ]), categorical),
    ])
    model = Pipeline([
        ("preprocessor", preprocessor),
        ("classifier", LogisticRegression(
            class_weight="balanced", max_iter=2000, random_state=RANDOM_STATE
        )),
    ])
    model.fit(X_train, y_train)

    probabilities = model.predict_proba(X_test)[:, 1]
    predictions = (probabilities >= SCREENING_THRESHOLD).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_test, predictions, labels=[0, 1]).ravel()
    metrics = {
        "model": "Balanced Logistic Regression",
        "positiveClass": "Churned=Yes",
        "rows": int(len(df)),
        "trainRows": int(len(X_train)),
        "holdoutRows": int(len(X_test)),
        "holdoutChurned": int(y_test.sum()),
        "screeningThreshold": SCREENING_THRESHOLD,
        "highRiskKpiThreshold": HIGH_RISK_THRESHOLD,
        "accuracy": float(accuracy_score(y_test, predictions)),
        "precision": float(precision_score(y_test, predictions, zero_division=0)),
        "recall": float(recall_score(y_test, predictions, zero_division=0)),
        "f1": float(f1_score(y_test, predictions, zero_division=0)),
        "confusionMatrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
        "split": "stratified 80/20, random_state=42",
        "note": "Holdout metrics measured at the preselected 0.40 screening threshold; the 0.50 high-risk dashboard threshold is separate.",
    }

    joblib.dump(model, MODEL_PATH)
    POLICY_PATH.write_text(json.dumps({
        "screeningThreshold": SCREENING_THRESHOLD,
        "highRiskKpiThreshold": HIGH_RISK_THRESHOLD,
        "positiveClass": "Yes",
        "model": "balanced_logistic_final.joblib",
        "split": "stratified 80/20, random_state=42",
    }, indent=2), encoding="utf-8")
    METRICS_PATH.write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    print(json.dumps(metrics, indent=2))
    print(f"Saved model: {MODEL_PATH}")
    print(f"Saved decision policy: {POLICY_PATH}")
    print(f"Saved holdout metrics: {METRICS_PATH}")


if __name__ == "__main__":
    main()
