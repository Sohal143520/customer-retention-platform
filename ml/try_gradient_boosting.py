
from pathlib import Path

import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.impute import SimpleImputer
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
    X_temp, y_temp, test_size=0.50,
    random_state=42, stratify=y_temp
)

preprocessor = ColumnTransformer([
    ("numeric", Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
    ]), numeric_cols),
    ("categorical", Pipeline([
        ("imputer", SimpleImputer(strategy="most_frequent")),
        ("encoder", OneHotEncoder(handle_unknown="ignore")),
    ]), categorical_cols),
])

model = Pipeline([
    ("preprocessor", preprocessor),
    ("classifier", GradientBoostingClassifier(
        n_estimators=100,
        learning_rate=0.05,
        max_depth=2,
        min_samples_leaf=10,
        subsample=0.8,
        random_state=42,
    )),
])

model.fit(X_train, y_train)
scores = model.predict_proba(X_val)[:, 1]

print("Gradient Boosting — validation results")
print("ROC-AUC:", round(roc_auc_score(y_val, scores), 4))
print("Average precision:", round(
    average_precision_score(y_val, scores), 4
))
print("Positive rate baseline:", round(y_val.mean(), 4))
print("Minimum probability:", round(scores.min(), 4))
print("Median probability:", round(float(pd.Series(scores).median()), 4))
print("Maximum probability:", round(scores.max(), 4))
print("\nValidation complete. Test set was not used.")
