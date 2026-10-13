
from pathlib import Path

import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, average_precision_score
from sklearn.model_selection import train_test_split
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
    X_temp, y_temp, test_size=0.50, random_state=42,
    stratify=y_temp
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
    "Dummy baseline": DummyClassifier(strategy="prior"),
    "Logistic Regression": Pipeline([
        ("preprocessor", make_preprocessor()),
        ("classifier", LogisticRegression(
            class_weight="balanced",
            max_iter=2000,
            random_state=42,
        )),
    ]),
    "Random Forest": Pipeline([
        ("preprocessor", make_preprocessor()),
        ("classifier", RandomForestClassifier(
            n_estimators=300,
            min_samples_leaf=5,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
        )),
    ]),
}

print("Validation positive rate:", round(y_val.mean(), 4))
print("\n=== VALIDATION RANKING METRICS ===")

for name, model in models.items():
    model.fit(X_train, y_train)
    scores = model.predict_proba(X_val)[:, 1]

    print(f"\n{name}")
    print("ROC-AUC:", round(roc_auc_score(y_val, scores), 4))
    print("Average precision:", round(
        average_precision_score(y_val, scores), 4
    ))
    print("Probability min:", round(scores.min(), 4))
    print("Probability median:", round(
        float(pd.Series(scores).median()), 4
    ))
    print("Probability max:", round(scores.max(), 4))

print("\n=== TRAINING DATA CHURN RATES ===")

train_df = X_train.copy()
train_df["Churned"] = y_train.to_numpy()

for column in categorical_cols:
    print(f"\n{column}:")
    print(
        train_df.groupby(column, observed=True)["Churned"]
        .agg(["mean", "count"])
        .round(3)
        .to_string()
    )

print("\nDiagnostic complete. Test set was not used.")
