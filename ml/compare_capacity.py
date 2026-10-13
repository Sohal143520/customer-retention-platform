from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    fbeta_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = (
    BASE_DIR.parent / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

df = pd.read_excel(DATA_PATH)
X = df.drop(columns=["Churned", "Customer_ID"])
y = (df["Churned"] == "Yes").astype(int)

categorical_cols = X.select_dtypes(
    include=["object", "category", "string"]
).columns.tolist()
numeric_cols = X.select_dtypes(include=["number"]).columns.tolist()

X_train, X_temp, y_train, y_temp = train_test_split(
    X, y, test_size=0.40, random_state=42, stratify=y
)
X_val, _, y_val, _ = train_test_split(
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

models = {
    "Logistic Regression": (
        Pipeline([
            ("preprocessor", make_preprocessor()),
            ("classifier", LogisticRegression(
                max_iter=2000, random_state=42
            )),
        ]),
        {
            "classifier__C": [0.01, 0.1, 1, 10],
            "classifier__class_weight": ["balanced", None],
        },
    ),
    "Gradient Boosting": (
        Pipeline([
            ("preprocessor", make_preprocessor()),
            ("classifier", GradientBoostingClassifier(
                random_state=42
            )),
        ]),
        {
            "classifier__n_estimators": [100, 200],
            "classifier__learning_rate": [0.03, 0.05, 0.1],
            "classifier__max_depth": [1, 2],
            "classifier__min_samples_leaf": [10, 20],
        },
    ),
}

capacity = 0.30
target_count = int(round(len(X_val) * capacity))

print(f"Training rows: {len(X_train)}")
print(f"Validation rows: {len(X_val)}")
print("Test rows reserved and not evaluated.")
print(f"Target capacity: {capacity:.0%} = {target_count} customers")
print("Selection criterion: validation recall at fixed capacity")
print("Positive class: Churned = Yes")

results = []

for name, (pipeline, param_grid) in models.items():
    print(f"\n{'=' * 58}")
    print(name)
    print("=" * 58)

    search = GridSearchCV(
        pipeline,
        param_grid,
        scoring="average_precision",
        cv=3,
        n_jobs=-1,
        refit=True,
    )
    search.fit(X_train, y_train)

    scores = search.best_estimator_.predict_proba(X_val)[:, 1]

    # Rank validation customers and select exactly the top 30%.
    ranked_indices = np.argsort(-scores, kind="stable")
    predictions = np.zeros(len(y_val), dtype=int)
    predictions[ranked_indices[:target_count]] = 1

    precision = precision_score(y_val, predictions, zero_division=0)
    recall = recall_score(y_val, predictions, zero_division=0)
    f2 = fbeta_score(y_val, predictions, beta=2, zero_division=0)
    matrix = confusion_matrix(y_val, predictions, labels=[0, 1])

    print("Best training CV average precision:",
          round(search.best_score_, 4))
    print("Best parameters:", search.best_params_)
    print("Validation ROC-AUC:", round(roc_auc_score(y_val, scores), 4))
    print("Validation average precision:",
          round(average_precision_score(y_val, scores), 4))
    print(f"Customers flagged: {predictions.sum()} / {len(y_val)}")
    print(f"Actual churners caught: {int(((predictions == 1) & (y_val.to_numpy() == 1)).sum())}")
    print(f"Precision: {precision:.4f}")
    print(f"Recall:    {recall:.4f}")
    print(f"F2 score:  {f2:.4f}")
    print("Confusion matrix [[TN, FP], [FN, TP]]:")
    print(matrix)

    results.append({
        "model": name,
        "recall": recall,
        "precision": precision,
        "F2": f2,
        "ROC-AUC": roc_auc_score(y_val, scores),
        "average_precision": average_precision_score(y_val, scores),
    })

print("\n" + "=" * 58)
print("VALIDATION COMPARISON AT 30% CAPACITY")
print("=" * 58)
comparison = pd.DataFrame(results).sort_values(
    ["recall", "F2", "precision"], ascending=False
)
print(comparison.round(4).to_string(index=False))
print("\nComparison complete. Test data was not used for selection or evaluation.")
