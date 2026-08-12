import argparse
import os
import re
import sys
from dataclasses import dataclass

import pandas as pd

def _project_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


# Ensure imports work when running as a script: `python training/train_model.py ...`
if _project_root() not in sys.path:
    sys.path.insert(0, _project_root())

from app.training import train_from_transactions_df  # noqa: E402


@dataclass(frozen=True)
class TrainArtifacts:
    iso_model: IsolationForest
    lof_model: LocalOutlierFactor
    svm_model: OneClassSVM
    scaler: StandardScaler
    feature_columns: list[str]


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Train anomaly detection models from an Excel file.")
    p.add_argument(
        "--input-file",
        required=True,
        help="Path to historical transactions file (.csv, .xlsx, .xls).",
    )
    # Backward-compatible alias
    p.add_argument(
        "--excel",
        required=False,
        help="(Deprecated) Alias for --input-file. Prefer --input-file.",
    )
    p.add_argument("--sheet", default=0, help="Excel sheet name or index (default: 0).")
    p.add_argument("--models-dir", default=os.path.join(os.path.dirname(__file__), "..", "models"))
    return p.parse_args()


def load_input(path: str, sheet) -> pd.DataFrame:
    ext = os.path.splitext(path)[1].lower()
    if ext == ".csv":
        df = pd.read_csv(path)
    elif ext in (".xlsx", ".xls"):
        df = pd.read_excel(path, sheet_name=sheet, engine="openpyxl")
    else:
        raise ValueError(f"Unsupported input file type: {ext}. Use .csv, .xlsx, or .xls")

    def normalize_col(name: str) -> str:
        name = str(name).strip()
        name = name.replace(" ", "_")
        # CamelCase -> snake_case
        name = re.sub(r"(.)([A-Z][a-z]+)", r"\1_\2", name)
        name = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
        name = name.lower()
        name = re.sub(r"[^a-z0-9_]+", "_", name)
        name = re.sub(r"_+", "_", name).strip("_")
        return name

    df.columns = [normalize_col(c) for c in df.columns]
    required = {
        "transaction_id",
        "account_id",
        "transaction_amount",
        "transaction_type",
        "transaction_date",
        "location",
        "channel",
        "login_attempts",
        "account_balance",
    }
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"Missing required columns in input file: {missing}")
    return df


def main() -> None:
    args = parse_args()
    input_path = args.input_file or args.excel
    if args.excel and args.input_file and args.excel != args.input_file:
        raise ValueError("Provide only one of --input-file or --excel (deprecated), not both.")
    if not input_path:
        raise ValueError("Missing input file. Provide --input-file (or --excel as deprecated alias).")

    df = load_input(input_path, args.sheet)
    trained_rows, feature_count = train_from_transactions_df(df, os.path.abspath(args.models_dir))
    print(f"Saved models to: {os.path.abspath(args.models_dir)}")
    print(f"Trained rows: {trained_rows}")
    print(f"Feature columns: {feature_count}")


if __name__ == "__main__":
    main()
