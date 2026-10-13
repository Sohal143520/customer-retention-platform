
from pathlib import Path

import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    precision_score,
    recall_score,
    f1_score,
    fbeta_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
DATA_PATH = (
    PROJECT_DIR
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

RANDOM_STATE = 42

# Load dataset and validate target.
df = pd.read_excel(DATA_PATH)

if "Churned" not in df.columns:
    raise ValueError("Churned column not found.")

if df["Churned"].isna().any():
    raise ValueError("Target contains missing values.")

X = df.drop(columns=["Churned", "Customer_ID"])
y = (df["Churned"] == "Yes").astype(int)

categorical_cols = X.select_dtypes(
    include=["object", "category", "string"]
).columns.tolist()

numeric_cols = X.select_dtypes(
    include=["number"]
).columns.tolist()

# Reproduce the original stratified splits.
X_train, X_temp, y_train, y_temp = train_test_split(
    X,
    y,
    test_size=0.40,
    random_state=RANDOM_STATE,
    stratify=y,
)

X_val, X_test, y_val, y_test = train_test_split(
    X_temp,
    y_temp,
    test_size=0.50,
    random_state=RANDOM_STATE,
    stratify=y_temp,
)

def make_preprocessor():
    numeric_pipeline = Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
    ])

    categorical_pipeline = Pipeline([
        ("imputer", SimpleImputer(strategy="most_frequent")),
        ("encoder", OneHotEncoder(handle_unknown="ignore")),
    ])

    return ColumnTransformer([
        ("numeric", numeric_pipeline, numeric_cols),
        ("categorical", categorical_pipeline, categorical_cols),
    ])


models = {
    "Logistic Regression": LogisticRegression(
        class_weight="balanced",
        max_iter=2000,
        random_state=RANDOM_STATE,
    ),
    "Random Forest": RandomForestClassifier(
        n_estimators=300,
        min_samples_leaf=5,
        class_weight="balanced",
        random_state=RANDOM_STATE,
        n_jobs=-1,
    ),
}

all_results = []

for model_name, classifier in models.items():
    pipeline = Pipeline([
        ("preprocessor", make_preprocessor()),
        ("classifier", classifier),
    ])

    pipeline.fit(X_train, y_train)
    probabilities = pipeline.predict_proba(X_val)[:, 1]

    for threshold in [i / 100 for i in range(10, 81, 5)]:
        predictions = (probabilities >= threshold).astype(int)

        all_results.append({
            "model": model_name,
            "threshold": threshold,
            "recall": recall_score(
                y_val, predictions, zero_division=0
            ),
            "precision": precision_score(
                y_val, predictions, zero_division=0
            ),
            "f1": f1_score(
                y_val, predictions, zero_division=0
            ),
            "f2": fbeta_score(
                y_val, predictions, beta=2, zero_division=0
            ),
            "predicted_churn_rate": predictions.mean(),
        })

results = pd.DataFrame(all_results)

print("\n=== VALIDATION COMPARISON ===")
print(
    results.round(3).to_string(index=False)
)

print("\n=== BEST F2 THRESHOLD PER MODEL ===")
for model_name in models:
    subset = results[results["model"] == model_name]
    best = subset.sort_values(
        ["f2", "precision", "recall"],
        ascending=False,
    ).iloc[0]

    print(
        f"\n{model_name}: "
        f"threshold={best['threshold']:.2f}, "
        f"recall={best['recall']:.3f}, "
        f"precision={best['precision']:.3f}, "
        f"F1={best['f1']:.3f}, "
        f"F2={best['f2']:.3f}, "
        f"predicted churn rate="
        f"{best['predicted_churn_rate']:.3f}"
    )

print("\nTest set remains untouched for model selection.")
