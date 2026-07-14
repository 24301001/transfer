from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

from traffic_dispatch_rl.config import (
    DATA_DIR,
    EMS_PROCESSED,
    EMS_RAW_DEFAULT,
    FIRE_PROCESSED,
    FIRE_RAW_DEFAULT,
)


EMS_USECOLS = [
    "INCIDENT_DATETIME",
    "INITIAL_CALL_TYPE",
    "INITIAL_SEVERITY_LEVEL_CODE",
    "FINAL_CALL_TYPE",
    "FINAL_SEVERITY_LEVEL_CODE",
    "VALID_DISPATCH_RSPNS_TIME_INDC",
    "DISPATCH_RESPONSE_SECONDS_QY",
    "VALID_INCIDENT_RSPNS_TIME_INDC",
    "INCIDENT_RESPONSE_SECONDS_QY",
    "INCIDENT_TRAVEL_TM_SECONDS_QY",
    "HELD_INDICATOR",
    "BOROUGH",
    "INCIDENT_DISPATCH_AREA",
    "ZIPCODE",
    "SPECIAL_EVENT_INDICATOR",
    "STANDBY_INDICATOR",
    "TRANSFER_INDICATOR",
]

FIRE_USECOLS = [
    "INCIDENT_DATETIME",
    "INCIDENT_BOROUGH",
    "ZIPCODE",
    "ALARM_SOURCE_DESCRIPTION_TX",
    "ALARM_LEVEL_INDEX_DESCRIPTION",
    "HIGHEST_ALARM_LEVEL",
    "INCIDENT_CLASSIFICATION",
    "INCIDENT_CLASSIFICATION_GROUP",
    "DISPATCH_RESPONSE_SECONDS_QY",
    "VALID_DISPATCH_RSPNS_TIME_INDC",
    "VALID_INCIDENT_RSPNS_TIME_INDC",
    "INCIDENT_RESPONSE_SECONDS_QY",
    "INCIDENT_TRAVEL_TM_SECONDS_QY",
    "ENGINES_ASSIGNED_QUANTITY",
    "LADDERS_ASSIGNED_QUANTITY",
    "OTHER_UNITS_ASSIGNED_QUANTITY",
]


def _num(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series.astype(str).str.replace(",", "", regex=False), errors="coerce")


def _flag(series: pd.Series) -> pd.Series:
    return series.astype(str).str.upper().map({"Y": 1, "N": 0}).fillna(0).astype("int8")


def _cat(series: pd.Series) -> pd.Series:
    values = series.astype(str).fillna("UNKNOWN")
    return (pd.util.hash_pandas_object(values, index=False) % 10_000).astype("int32")


def _time_features(df: pd.DataFrame) -> pd.DataFrame:
    dt = pd.to_datetime(df["INCIDENT_DATETIME"], format="%m/%d/%Y %I:%M:%S %p", errors="coerce")
    df["month"] = dt.dt.month.fillna(0).astype("int8")
    df["weekday"] = dt.dt.weekday.fillna(0).astype("int8")
    df["hour"] = dt.dt.hour.fillna(0).astype("int8")
    return df


def preprocess_ems(raw_path: Path, out_path: Path, chunksize: int, sample_frac: float) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    first = True

    for chunk in pd.read_csv(raw_path, usecols=EMS_USECOLS, chunksize=chunksize, low_memory=False):
        if sample_frac < 1.0:
            chunk = chunk.sample(frac=sample_frac, random_state=42)

        chunk = _time_features(chunk)
        severity = _num(chunk["FINAL_SEVERITY_LEVEL_CODE"]).fillna(
            _num(chunk["INITIAL_SEVERITY_LEVEL_CODE"])
        )

        # EMS codes are kept as raw codes and normalized as a model feature.
        # If a local dataset defines severity in the opposite direction, only this line needs adjustment.
        chunk["severity_code"] = severity.fillna(severity.median()).fillna(0)
        chunk["severity_norm"] = (chunk["severity_code"] / 9.0).clip(0, 1)

        chunk["call_type"] = _cat(chunk["FINAL_CALL_TYPE"].where(chunk["FINAL_CALL_TYPE"].notna(), chunk["INITIAL_CALL_TYPE"]))
        chunk["borough"] = _cat(chunk["BOROUGH"])
        chunk["dispatch_area"] = _cat(chunk["INCIDENT_DISPATCH_AREA"])
        chunk["zipcode"] = _num(chunk["ZIPCODE"]).fillna(0)
        chunk["special_event"] = _flag(chunk["SPECIAL_EVENT_INDICATOR"])
        chunk["standby"] = _flag(chunk["STANDBY_INDICATOR"])
        chunk["transfer"] = _flag(chunk["TRANSFER_INDICATOR"])
        chunk["held"] = _flag(chunk["HELD_INDICATOR"])
        chunk["valid_dispatch"] = _flag(chunk["VALID_DISPATCH_RSPNS_TIME_INDC"])
        chunk["valid_response"] = _flag(chunk["VALID_INCIDENT_RSPNS_TIME_INDC"])
        chunk["dispatch_seconds"] = _num(chunk["DISPATCH_RESPONSE_SECONDS_QY"])
        chunk["response_seconds"] = _num(chunk["INCIDENT_RESPONSE_SECONDS_QY"])
        chunk["travel_seconds"] = _num(chunk["INCIDENT_TRAVEL_TM_SECONDS_QY"])

        clean = chunk[
            [
                "month",
                "weekday",
                "hour",
                "severity_code",
                "severity_norm",
                "call_type",
                "borough",
                "dispatch_area",
                "zipcode",
                "special_event",
                "standby",
                "transfer",
                "held",
                "valid_dispatch",
                "valid_response",
                "dispatch_seconds",
                "response_seconds",
                "travel_seconds",
            ]
        ].replace([np.inf, -np.inf], np.nan)
        clean = clean.dropna(subset=["response_seconds"])
        clean.to_csv(out_path, index=False, mode="w" if first else "a", header=first)
        first = False


def preprocess_fire(raw_path: Path, out_path: Path, chunksize: int, sample_frac: float) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    first = True

    for chunk in pd.read_csv(raw_path, usecols=FIRE_USECOLS, chunksize=chunksize, low_memory=False):
        if sample_frac < 1.0:
            chunk = chunk.sample(frac=sample_frac, random_state=43)

        chunk = _time_features(chunk)
        chunk["borough"] = _cat(chunk["INCIDENT_BOROUGH"])
        chunk["zipcode"] = _num(chunk["ZIPCODE"]).fillna(0)
        chunk["alarm_source"] = _cat(chunk["ALARM_SOURCE_DESCRIPTION_TX"])
        chunk["alarm_index"] = _cat(chunk["ALARM_LEVEL_INDEX_DESCRIPTION"])
        chunk["highest_alarm"] = _cat(chunk["HIGHEST_ALARM_LEVEL"])
        chunk["classification"] = _cat(chunk["INCIDENT_CLASSIFICATION"])
        chunk["classification_group"] = _cat(chunk["INCIDENT_CLASSIFICATION_GROUP"])
        chunk["valid_dispatch"] = _flag(chunk["VALID_DISPATCH_RSPNS_TIME_INDC"])
        chunk["valid_response"] = _flag(chunk["VALID_INCIDENT_RSPNS_TIME_INDC"])
        chunk["dispatch_seconds"] = _num(chunk["DISPATCH_RESPONSE_SECONDS_QY"])
        chunk["response_seconds"] = _num(chunk["INCIDENT_RESPONSE_SECONDS_QY"])
        chunk["travel_seconds"] = _num(chunk["INCIDENT_TRAVEL_TM_SECONDS_QY"])
        chunk["engines"] = _num(chunk["ENGINES_ASSIGNED_QUANTITY"]).fillna(0)
        chunk["ladders"] = _num(chunk["LADDERS_ASSIGNED_QUANTITY"]).fillna(0)
        chunk["other_units"] = _num(chunk["OTHER_UNITS_ASSIGNED_QUANTITY"]).fillna(0)

        clean = chunk[
            [
                "month",
                "weekday",
                "hour",
                "borough",
                "zipcode",
                "alarm_source",
                "alarm_index",
                "highest_alarm",
                "classification",
                "classification_group",
                "valid_dispatch",
                "valid_response",
                "dispatch_seconds",
                "response_seconds",
                "travel_seconds",
                "engines",
                "ladders",
                "other_units",
            ]
        ].replace([np.inf, -np.inf], np.nan)
        clean = clean.dropna(subset=["response_seconds"])
        clean.to_csv(out_path, index=False, mode="w" if first else "a", header=first)
        first = False


def main() -> None:
    parser = argparse.ArgumentParser(description="Build compact RL training tables from raw dispatch CSVs.")
    parser.add_argument(
        "--ems-raw", type=Path, default=EMS_RAW_DEFAULT,
        help="Path to raw EMS CSV. Can also set EMS_RAW_PATH env variable.",
    )
    parser.add_argument(
        "--fire-raw", type=Path, default=FIRE_RAW_DEFAULT,
        help="Path to raw Fire CSV. Can also set FIRE_RAW_PATH env variable.",
    )
    parser.add_argument("--chunksize", type=int, default=100_000)
    parser.add_argument("--sample-frac", type=float, default=0.01, help="Use 1.0 for full data.")
    parser.add_argument("--only", choices=["all", "ems", "fire"], default="all")
    args = parser.parse_args()

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if args.only in {"all", "ems"}:
        if args.ems_raw is None:
            parser.error(
                "EMS raw data path required. Use --ems-raw PATH or set EMS_RAW_PATH env variable."
            )
        preprocess_ems(args.ems_raw, EMS_PROCESSED, args.chunksize, args.sample_frac)
        print(f"EMS processed data saved to {EMS_PROCESSED}")
    if args.only in {"all", "fire"}:
        if args.fire_raw is None:
            parser.error(
                "Fire raw data path required. Use --fire-raw PATH or set FIRE_RAW_PATH env variable."
            )
        preprocess_fire(args.fire_raw, FIRE_PROCESSED, args.chunksize, args.sample_frac)
        print(f"Fire processed data saved to {FIRE_PROCESSED}")


if __name__ == "__main__":
    main()
