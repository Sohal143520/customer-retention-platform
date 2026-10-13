
from pathlib import Path

import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
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
    BASE_DIR.parent
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

df = pd.read_excel(DATA_PATH)

X = df.drop(columns=["Churned", "Customer_ID"])
y = (df["Churned"] == "Yes").astype(int)

categorical_cols = X.select_dtypes(
    include=["object", "category", "string"]
).columns.tolist()

numeric_cols = X.select_dtypes(
    include=["number"]
).columns.tolist()

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
                max_iter=2000,
                random_state=42,
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
                random_state=42,
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

thresholds = [
    0.20, 0.25, 0.30, 0.35, 0.40,
    0.45, 0.50, 0.55, 0.60, 0.65,
]

print("Training rows:", len(X_train))
print("Validation rows:", len(X_val))
print("Test rows reserved:", len(df) - len(X_train) - len(X_val))
print("Tuning objective: 3-fold training average precision")
print("Positive class: Churned = Yes")

for name, (pipeline, param_grid) in models.items():
    print(f"\n{'=' * 60}")
    print(name)
    print("=" * 60)

    search = GridSearchCV(
        estimator=pipeline,
        param_grid=param_grid,
        scoring="average_precision",
        cv=3,
        n_jobs=-1,
        refit=True,
        verbose=0,
    )

    search.fit(X_train, y_train)

    best_model = search.best_estimator_
    val_scores = best_model.predict_proba(X_val)[:, 1]

    print(
        "Best training CV average precision:",
        round(search.best_score_, 4),
    )
    print("Best parameters:", search.best_params_)
    print(
        "Validation ROC-AUC:",
        round(roc_auc_score(y_val, val_scores), 4),
    )
    print(
        "Validation average precision:",
        round(average_precision_score(y_val, val_scores), 4),
    )

    rows = []

    for threshold in thresholds:
        predictions = (val_scores >= threshold).astype(int)

        precision = precision_score(
            y_val, predictions, zero_division=0
        )
        recall = recall_score(
            y_val, predictions, zero_division=0
        )
        f2 = fbeta_score(
            y_val, predictions, beta=2, zero_division=0
        )

        rows.append({
            "threshold": threshold,
            "precision": precision,
            "recall": recall,
            "F2": f2,
            "predicted_churn_rate": predictions.mean(),
        })

    results = pd.DataFrame(rows).sort_values(
        ["F2", "precision"], ascending=False
    )

    print("\nValidation thresholds, ranked by F2:")
    print(results.round(3).to_string(index=False))

    best = results.iloc[0]

    print("\nBest validation threshold by F2:", best["threshold"])
    print("Validation precision:", round(best["precision"], 4))
    print("Validation recall:", round(best["recall"], 4))
    print("Validation F2:", round(best["F2"], 4))
    print(
        "Predicted churn rate:",
        round(best["predicted_churn_rate"], 4),
    )

print("\nTuning complete. The test set was not used.")
print("No application or authentication files were changed.")
