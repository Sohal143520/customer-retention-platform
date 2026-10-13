from pathlib import Path
import json
import joblib
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    fbeta_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

import os
BASE_DIR = Path(__file__).resolve().parent
# Allow override via env var for containerized training
DATA_PATH = Path(os.getenv("TRAIN_DATA_PATH", BASE_DIR.parent / "dataset" / "Client Data Set for Student Project Workbook.xlsx"))
MODEL_PATH = BASE_DIR / "gradient_boosting_final.joblib"
POLICY_PATH = BASE_DIR / "targeting_policy.json"

df = pd.read_excel(DATA_PATH)
X = df.drop(columns=["Churned", "Customer_ID"])
y = (df["Churned"] == "Yes").astype(int)

categorical_cols = X.select_dtypes(
    include=["object", "category", "string"]
).columns.tolist()
numeric_cols = X.select_dtypes(include=["number"]).columns.tolist()

# Reproduce the established stratified 60/20/20 split.
X_train, X_temp, y_train, y_temp = train_test_split(
    X, y, test_size=0.40, random_state=42, stratify=y
)
X_val, X_test, y_val, y_test = train_test_split(
    X_temp, y_temp, test_size=0.50,
    random_state=42, stratify=y_temp
)

def make_preprocessor():
    return ColumnTransformer([
        ("numeric", Pipeline([
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]), numeric_cols),
        ("categorical", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("encoder", OneHotEncoder(handle_unknown="ignore")),
        ]), categorical_cols),
    ])

pipeline = Pipeline([
    ("preprocessor", make_preprocessor()),
    ("classifier", GradientBoostingClassifier(random_state=42)),
])

param_grid = {
    "classifier__n_estimators": [100, 200],
    "classifier__learning_rate": [0.03, 0.05, 0.1],
    "classifier__max_depth": [1, 2],
    "classifier__min_samples_leaf": [10, 20],
}

print("Selecting Gradient Boosting parameters using training data only...")
search = GridSearchCV(
    pipeline,
    param_grid,
    scoring="average_precision",
    cv=3,
    n_jobs=-1,
    refit=True,
)
search.fit(X_train, y_train)
model = search.best_estimator_

# Derive the ranking policy on validation only.
val_scores = model.predict_proba(X_val)[:, 1]
capacity = 0.30
target_count = int(round(len(X_val) * capacity))
val_order = val_scores.argsort()[::-1]
val_pred = pd.Series(0, index=y_val.index, dtype=int)
val_pred.iloc[:] = 0
val_pred.loc[X_val.index[val_order[:target_count]]] = 1

# Store a descriptive policy: rank scores and contact top 30%.
policy = {
    "model": "GradientBoostingClassifier",
    "target_class": "Yes",
    "targeting_capacity": capacity,
    "selection_method": "rank predicted churn probabilities; select top 30%",
    "validation_rows": int(len(X_val)),
    "validation_customers_targeted": int(target_count),
    "best_training_cv_average_precision": float(search.best_score_),
    "best_parameters": search.best_params_,
    "random_state": 42,
    "split": "stratified 60/20/20",
}
joblib.dump(model, MODEL_PATH)
POLICY_PATH.write_text(json.dumps(policy, indent=2), encoding="utf-8")

print("\nSaved model:", MODEL_PATH)
print("Saved policy:", POLICY_PATH)
print("Best parameters:", search.best_params_)
print("Training CV average precision:", round(search.best_score_, 4))
print("Validation capacity:", f"{target_count}/{len(X_val)}")
print("Validation precision:", round(precision_score(y_val, val_pred), 4))
print("Validation recall:", round(recall_score(y_val, val_pred), 4))
print("Validation F2:", round(fbeta_score(y_val, val_pred, beta=2), 4))

# One final test evaluation after candidate and policy are locked.
# Use a fixed 30% of test customers, ranked by their predicted scores.
test_scores = model.predict_proba(X_test)[:, 1]
test_target_count = int(round(len(X_test) * capacity))
test_order = test_scores.argsort()[::-1]
test_pred = pd.Series(0, index=y_test.index, dtype=int)
test_pred.loc[X_test.index[test_order[:test_target_count]]] = 1

print("\n" + "=" * 58)
print("FINAL TEST EVALUATION — DO NOT TUNE FROM THESE RESULTS")
print("=" * 58)
print("Test rows:", len(X_test))
print("Customers targeted:", int(test_pred.sum()), "/", len(X_test))
print("Actual churners:", int(y_test.sum()))
print("Churners caught:", int(((test_pred == 1) & (y_test == 1)).sum()))
print("Precision:", round(precision_score(y_test, test_pred), 4))
print("Recall:", round(recall_score(y_test, test_pred), 4))
print("F1:", round(f1_score(y_test, test_pred), 4))
print("F2:", round(fbeta_score(y_test, test_pred, beta=2), 4))
print("ROC-AUC (ranking quality):", round(roc_auc_score(y_test, test_scores), 4))
print("Average precision:", round(average_precision_score(y_test, test_scores), 4))
print("Confusion matrix [[TN, FP], [FN, TP]]:")
print(confusion_matrix(y_test, test_pred, labels=[0, 1]))
print("\nFinalization complete. Test results are for reporting only.")
