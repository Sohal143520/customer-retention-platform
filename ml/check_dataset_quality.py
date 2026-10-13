from pathlib import Path
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = (
    BASE_DIR.parent
    / "dataset"
    / "Client Data Set for Student Project Workbook.xlsx"
)

df = pd.read_excel(DATA_PATH)

print("=== DATASET QUALITY CHECK ===")
print("Rows:", len(df))
print("Columns:", len(df.columns))
print("Duplicate rows:", df.duplicated().sum())
print("Duplicate Customer_IDs:", df["Customer_ID"].duplicated().sum())
print("Missing values:", int(df.isna().sum().sum()))

print("\nTarget labels:")
print(df["Churned"].value_counts(dropna=False).to_string())

print("\nUnique values in categorical features:")
for col in ["Age_Group", "Region", "Customer_Segment"]:
    print(f"\n{col}:")
    print(df[col].value_counts(dropna=False).to_string())

print("\nNumeric feature ranges:")
numeric_cols = df.drop(
    columns=["Customer_ID", "Churned"]
).select_dtypes(include="number").columns

print(
    df[numeric_cols]
    .describe()
    .round(2)
    .to_string()
)

print("\nDataset quality check complete.")