
from pathlib import Path
import json

import joblib
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    precision_score,
    recall_score,
    f1_score,
    fbeta_score,
)

BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
DATA_PATH = (
    PROJECT_DIR
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

TARGET = "Churned"
RANDOM_STATE = 42

# 1. Load and validate the dataset.
df = pd.read_excel(DATA_PATH)

if TARGET not in df.columns:
    raise ValueError(f"Target column '{TARGET}' not found.")

if df[TARGET].isna().any():
    raise ValueError("Target contains missing values.")

if not set(df[TARGET].unique()).issubset({"Yes", "No"}):
    raise ValueError("Churned must contain only 'Yes' and 'No'.")

# 2. Exclude the identifier and separate target from predictors.
X = df.drop(columns=[TARGET, "Customer_ID"])
y = (df[TARGET] == "Yes").astype(int)

categorical_cols = X.select_dtypes(
    include=["object", "category", "string"]
).columns.tolist()

numeric_cols = X.select_dtypes(
    include=["number"]
).columns.tolist()

if len(categorical_cols) + len(numeric_cols) != X.shape[1]:
    raise ValueError("Some feature columns have unsupported data types.")

# 3. Create stratified train, validation, and test sets.
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

# 4. Preprocess numeric and categorical columns separately.
numeric_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="median")),
    ("scaler", StandardScaler()),
])

categorical_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="most_frequent")),
    ("encoder", OneHotEncoder(handle_unknown="ignore")),
])

preprocessor = ColumnTransformer([
    ("numeric", numeric_pipeline, numeric_cols),
    ("categorical", categorical_pipeline, categorical_cols),
])

# 5. Train a logistic regression baseline.
model = Pipeline([
    ("preprocessor", preprocessor),
    (
        "classifier",
        LogisticRegression(
            class_weight="balanced",
            max_iter=2000,
            random_state=RANDOM_STATE,
        ),
    ),
])

model.fit(X_train, y_train)

# 6. Select a recall-oriented threshold using validation data only.
val_probabilities = model.predict_proba(X_val)[:, 1]

threshold_results = []

for threshold in [i / 100 for i in range(10, 81, 5)]:
    val_predictions = (val_probabilities >= threshold).astype(int)

    threshold_results.append({
        "threshold": threshold,
        "recall": recall_score(
            y_val, val_predictions, zero_division=0
        ),
        "precision": precision_score(
            y_val, val_predictions, zero_division=0
        ),
        "f1": f1_score(
            y_val, val_predictions, zero_division=0
        ),
        "f2": fbeta_score(
            y_val, val_predictions, beta=2, zero_division=0
        ),
    })

print("\n=== VALIDATION THRESHOLD COMPARISON ===")
print(
    pd.DataFrame(threshold_results)
    .round(3)
    .to_string(index=False)
)

# F2 emphasizes recall more than precision.
# Use precision as a tie-breaker.
best = max(
    threshold_results,
    key=lambda row: (row["f2"], row["precision"]),
)

threshold = best["threshold"]

# 7. Evaluate exactly once on the held-out test set.
test_probabilities = model.predict_proba(X_test)[:, 1]
test_predictions = (test_probabilities >= threshold).astype(int)

print("\n=== DATASET ===")
print("Rows:", len(df))
print("Predictor columns:", X.shape[1])
print("Categorical columns:", categorical_cols)
print("Numeric columns:", numeric_cols)
print("Churn counts:")
print(df[TARGET].value_counts())

print("\n=== SPLIT SIZES ===")
print("Training:", len(X_train))
print("Validation:", len(X_val))
print("Test:", len(X_test))

print("\n=== SELECTED VALIDATION THRESHOLD ===")
print(json.dumps(best, indent=2))

print("\n=== HELD-OUT TEST RESULTS ===")
print("Positive class: Churned = Yes")
print("Threshold:", threshold)
print("Precision:", round(
    precision_score(y_test, test_predictions, zero_division=0), 4
))
print("Recall:", round(
    recall_score(y_test, test_predictions, zero_division=0), 4
))
print("F1-score:", round(
    f1_score(y_test, test_predictions, zero_division=0), 4
))
print("\nConfusion matrix [ [TN, FP], [FN, TP] ]:")
print(confusion_matrix(y_test, test_predictions, labels=[0, 1]))
print("\nClassification report:")
print(classification_report(
    y_test,
    test_predictions,
    labels=[0, 1],
    target_names=["No churn", "Churn"],
    zero_division=0,
))

# 8. Save the fitted pipeline and selected threshold.
model_path = BASE_DIR / "churn_model.joblib"
threshold_path = BASE_DIR / "threshold.json"

joblib.dump(model, model_path)

threshold_path.write_text(
    json.dumps({
        "threshold": threshold,
        "positive_class": "Yes",
        "target": TARGET,
    }, indent=2),
    encoding="utf-8",
)

print("\nSaved model:", model_path)
print("Saved threshold:", threshold_path)
