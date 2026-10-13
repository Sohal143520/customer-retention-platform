from pathlib import Path
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = (
    BASE_DIR.parent
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

df = pd.read_excel(DATA_PATH)
df["Churn_Flag"] = (df["Churned"] == "Yes").astype(int)

features = [
    "Payment_Failures_90D",
    "Support_Tickets_90D",
    "Satisfaction_Score",
    "Days_Since_Last_Purchase",
    "Transactions_90D",
    "App_Logins_30D",
]

print("=== CHURN RATE BY FEATURE GROUP ===")

for col in features:
    df[f"{col}_Group"] = pd.qcut(
        df[col],
        q=4,
        duplicates="drop",
    )

    summary = (
        df.groupby(f"{col}_Group", observed=True)["Churn_Flag"]
        .agg(["mean", "count"])
        .rename(columns={"mean": "churn_rate"})
    )

    summary["churn_rate"] = (summary["churn_rate"] * 100).round(1)

    print(f"\n{col}:")
    print(summary.to_string())

print("\nPattern analysis complete.")