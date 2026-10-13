
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = (
    BASE_DIR.parent
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

df = pd.read_excel(DATA_PATH)

# Use training data only; keep validation and test data out.
train_df, _ = train_test_split(
    df,
    test_size=0.40,
    random_state=42,
    stratify=df["Churned"],
)

numeric_cols = train_df.drop(
    columns=["Customer_ID", "Churned"]
).select_dtypes(include="number").columns.tolist()

print("Training rows:", len(train_df))
print("\n=== NUMERIC FEATURES BY CHURN STATUS ===")

results = []

for col in numeric_cols:
    churned = train_df.loc[train_df["Churned"] == "Yes", col]
    retained = train_df.loc[train_df["Churned"] == "No", col]

    pooled_std = (
        (churned.var() + retained.var()) / 2
    ) ** 0.5

    difference = (
        (churned.mean() - retained.mean()) / pooled_std
        if pooled_std > 0 else 0
    )

    results.append({
        "Feature": col,
        "Churn mean": churned.mean(),
        "Retained mean": retained.mean(),
        "Churn median": churned.median(),
        "Retained median": retained.median(),
        "Standardized difference": difference,
        "Absolute difference": abs(difference),
    })

result_df = pd.DataFrame(results).sort_values(
    "Absolute difference", ascending=False
)

print(
    result_df.drop(columns=["Absolute difference"])
    .round(3)
    .to_string(index=False)
)

print("\nInterpretation guide:")
print("Larger absolute standardized differences indicate")
print("greater separation between the two groups.")
print("Positive: higher average among churned customers.")
print("Negative: lower average among churned customers.")
print("\nAnalysis complete. Validation and test sets were not used.")
