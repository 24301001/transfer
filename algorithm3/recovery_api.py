import math
import re
from datetime import datetime
from pathlib import Path
from typing import Any, Optional
from uuid import uuid4

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# pandas 版本兼容补丁：旧版 sklearn pipeline 序列化时可能引用
# pandas.core.strings.StringMethods，新版 pandas 已移除该路径。
# ---------------------------------------------------------------------------
if not hasattr(pd.core.strings, "StringMethods"):
    class _FakeStringMethods:
        pass
    pd.core.strings.StringMethods = _FakeStringMethods


BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = BASE_DIR / "al3.3"
DATA_PATH = MODEL_DIR / "accident_recovery_dataset.csv"
REGRESSION_MODEL_PATH = MODEL_DIR / "best_recovery_regression_model.pkl"
CLASSIFICATION_MODEL_PATH = MODEL_DIR / "best_recovery_classification_model.pkl"


class AttachmentPayload(BaseModel):
    id: Optional[int] = None
    attachmentId: Optional[int] = None
    originalFilename: Optional[str] = None
    contentType: Optional[str] = None
    fileSize: Optional[int] = None
    filePath: Optional[str] = None
    aiDetectedTypes: Optional[str] = None


class RecoveryPredictRequest(BaseModel):
    incidentId: Optional[int] = None
    incidentNo: Optional[str] = None
    description: Optional[str] = None
    accidentType: Optional[str] = None
    riskLevel: Optional[str] = None
    riskScore: Optional[float] = None
    congestionDurationMinutes: Optional[int] = None
    longitude: Optional[float] = None
    latitude: Optional[float] = None
    occupiedLanes: Optional[int] = None
    trafficFlow: Optional[int] = None
    peopleFlow: Optional[int] = None
    roadLevel: Optional[str] = None
    roadStatus: Optional[str] = None
    weather: Optional[str] = None
    sceneLabels: Optional[str] = None
    initialAccidentType: Optional[str] = None
    attachments: list[AttachmentPayload] = Field(default_factory=list)
    features: dict[str, Any] = Field(default_factory=dict)


regression_bundle: Optional[dict[str, Any]] = None
classification_bundle: Optional[dict[str, Any]] = None
feature_defaults: dict[str, float] = {}


def _safe_float(value: Any, fallback: Optional[float] = None) -> Optional[float]:
    try:
        if value is None or value == "":
            return fallback
        result = float(value)
        if math.isnan(result) or math.isinf(result):
            return fallback
        return result
    except (TypeError, ValueError):
        return fallback


def _safe_int(value: Any, fallback: int = 0) -> int:
    number = _safe_float(value)
    if number is None:
        return fallback
    return int(round(number))


def _contains(text: str, *keywords: str) -> bool:
    normalized = text.lower()
    return any(keyword.lower() in normalized for keyword in keywords)


def _risk_level_value(level: Optional[str], score: Optional[float]) -> int:
    normalized = (level or "").upper()
    if normalized in {"HIGH", "CRITICAL"}:
        return 2
    if normalized == "MEDIUM":
        return 1
    if normalized == "LOW":
        return 0
    numeric_score = _safe_float(score)
    if numeric_score is None:
        return 1
    if numeric_score >= 70:
        return 2
    if numeric_score >= 35:
        return 1
    return 0


def _road_type_value(road_level: Optional[str]) -> int:
    text = road_level or ""
    if _contains(text, "highway", "express", "freeway", "高速", "快速"):
        return 2
    if _contains(text, "main", "arterial", "主干", "干道"):
        return 1
    return 0


def _lane_status_value(road_status: Optional[str], occupied_lanes: Optional[int]) -> int:
    text = road_status or ""
    if _contains(text, "closed", "封闭", "阻断", "施工"):
        return 2
    if _contains(text, "wet", "ice", "congest", "湿滑", "积水", "结冰", "拥堵"):
        return 1
    if occupied_lanes and occupied_lanes > 0:
        return 1
    return 0


def _traffic_volume_value(flow: Optional[int]) -> int:
    if flow is None:
        return 2
    if flow >= 70:
        return 3
    if flow >= 35:
        return 2
    return 1


def _traffic_density_value(flow: Optional[int]) -> int:
    if flow is None:
        return 1
    if flow >= 70:
        return 2
    if flow >= 35:
        return 1
    return 0


def _attachment_text(attachments: list[AttachmentPayload]) -> str:
    parts: list[str] = []
    for attachment in attachments:
        if attachment.originalFilename:
            parts.append(attachment.originalFilename)
        if attachment.contentType:
            parts.append(attachment.contentType)
        if attachment.aiDetectedTypes:
            parts.append(attachment.aiDetectedTypes)
    return " ".join(parts)


def _build_features(request: RecoveryPredictRequest) -> dict[str, float]:
    now = datetime.now()
    text = " ".join(
        filter(
            None,
            [
                request.description,
                request.accidentType,
                request.initialAccidentType,
                request.sceneLabels,
                request.roadStatus,
                request.roadLevel,
                _attachment_text(request.attachments),
            ],
        )
    )

    occupied_lanes = max(0, _safe_int(request.occupiedLanes, 1))
    actual_lanes = max(occupied_lanes + 1, 2)
    traffic_flow = None if request.trafficFlow is None else _safe_int(request.trafficFlow)

    features = dict(feature_defaults)
    features.update(
        {
            "car_crash": 1 if _contains(text, "crash", "collision", "追尾", "碰撞", "撞", "事故") else 0,
            "car_damage": 1 if _contains(text, "damage", "damaged", "损坏", "受损", "变形", "刮擦") else 0,
            "fire": 1 if _contains(text, "fire", "smoke", "起火", "冒烟", "燃烧") else 0,
            "car_flip": 1 if _contains(text, "flip", "rollover", "overturn", "侧翻", "翻车") else 0,
            "car_num": max(1, len(re.findall(r"(car|轿车|小车|车辆)", text.lower())) or 1),
            "truck_num": len(re.findall(r"(truck|货车|卡车)", text.lower())),
            "bus_num": len(re.findall(r"(bus|公交|客车)", text.lower())),
            "motorcycle_num": len(re.findall(r"(motorcycle|摩托|电动车)", text.lower())),
            "risk_level": _risk_level_value(request.riskLevel, request.riskScore),
            "Month": now.month,
            "Day": now.day,
            "Hour": now.hour,
            "Weekday": now.weekday(),
            "Longitude": _safe_float(request.longitude, feature_defaults.get("Longitude", 0.0)),
            "Latitude": _safe_float(request.latitude, feature_defaults.get("Latitude", 0.0)),
            "Affected_Lanes": occupied_lanes,
            "Actual_Number_of_Lanes": actual_lanes,
            "lane_status": _lane_status_value(request.roadStatus, occupied_lanes),
            "Traffic_Volume": _traffic_volume_value(traffic_flow),
            "traffic_density": _traffic_density_value(traffic_flow),
            "road_type": _road_type_value(request.roadLevel),
        }
    )

    for key, value in request.features.items():
        numeric_value = _safe_float(value)
        if numeric_value is not None:
            features[key] = numeric_value

    return features


def _feature_frame(features: dict[str, float], expected_features: list[str]) -> pd.DataFrame:
    row = {}
    for name in expected_features:
        row[name] = _safe_float(features.get(name), feature_defaults.get(name, 0.0)) or 0.0
    return pd.DataFrame([row], columns=expected_features)


def _duration_band(minutes: float) -> str:
    if minutes >= 90:
        return "CRITICAL"
    if minutes >= 60:
        return "LONG"
    if minutes >= 30:
        return "MEDIUM"
    return "SHORT"


def _recommendation(band: str, request: RecoveryPredictRequest, minutes: int) -> str:
    lane_text = f"occupied lanes: {request.occupiedLanes}" if request.occupiedLanes is not None else "lane impact unknown"
    if band in {"CRITICAL", "LONG"}:
        return (
            f"Algorithm3 predicts about {minutes} minutes for road recovery. "
            f"Dispatch clearance resources early, protect key lanes first, and prepare diversion control; {lane_text}."
        )
    if band == "MEDIUM":
        return (
            f"Algorithm3 predicts about {minutes} minutes for road recovery. "
            f"Keep police guidance on scene and arrange clearance support if congestion expands; {lane_text}."
        )
    return (
        f"Algorithm3 predicts about {minutes} minutes for road recovery. "
        f"Use quick handling and warning isolation while keeping traffic moving; {lane_text}."
    )


def _key_factors(features: dict[str, float]) -> list[str]:
    labels = []
    if features.get("car_damage", 0) > 0:
        labels.append("vehicle damage")
    if features.get("car_crash", 0) > 0:
        labels.append("collision")
    if features.get("fire", 0) > 0:
        labels.append("fire or smoke")
    if features.get("car_flip", 0) > 0:
        labels.append("rollover")
    if features.get("Affected_Lanes", 0) >= 2:
        labels.append("multiple lanes affected")
    if features.get("traffic_density", 0) >= 2:
        labels.append("high traffic density")
    if features.get("risk_level", 0) >= 2:
        labels.append("high risk level")
    return labels[:6]


def _load_defaults() -> dict[str, float]:
    if not DATA_PATH.exists():
        return {}
    try:
        data = pd.read_csv(DATA_PATH)
        numeric = data.drop(columns=["duration"], errors="ignore").median(numeric_only=True)
        return {str(key): float(value) for key, value in numeric.to_dict().items()}
    except Exception:
        return {}


def _load_models() -> None:
    global regression_bundle, classification_bundle, feature_defaults

    if not REGRESSION_MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Regression model not found: {REGRESSION_MODEL_PATH}. "
            "Please run al3.3/compare_recovery_models.py first to generate model files."
        )

    feature_defaults = _load_defaults()
    regression_bundle = joblib.load(REGRESSION_MODEL_PATH)

    if not isinstance(regression_bundle, dict) or "model" not in regression_bundle:
        raise ValueError(
            f"Invalid regression model bundle: {REGRESSION_MODEL_PATH}. "
            "Expected a dict with 'model' key."
        )

    log_msg = (
        f"Algorithm3 regression model loaded: "
        f"{regression_bundle.get('best_model_name', 'unknown')}, "
        f"features={len(regression_bundle.get('features', []))}"
    )

    if CLASSIFICATION_MODEL_PATH.exists():
        try:
            classification_bundle = joblib.load(CLASSIFICATION_MODEL_PATH)
            if isinstance(classification_bundle, dict) and "model" in classification_bundle:
                log_msg += (
                    f" | classification: "
                    f"{classification_bundle.get('best_model_name', 'unknown')}, "
                    f"threshold={classification_bundle.get('threshold_minutes', 30)}min"
                )
            else:
                classification_bundle = None
                log_msg += " | classification: invalid bundle (skipped)"
        except Exception as exc:
            classification_bundle = None
            log_msg += f" | classification: load failed ({exc})"
    else:
        classification_bundle = None
        log_msg += " | classification: model file not found"

    print(log_msg)


app = FastAPI(
    title="Algorithm3 Road Recovery Recommendation API",
    description="Predicts road recovery duration and recommends clearance strategy.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup() -> None:
    _load_models()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP" if regression_bundle else "DOWN",
        "service": "algorithm3-road-recovery",
        "regressionModel": regression_bundle.get("best_model_name") if regression_bundle else None,
        "classificationModel": classification_bundle.get("best_model_name") if classification_bundle else None,
    }


@app.get("/api/health")
def api_health() -> dict[str, Any]:
    return health()


@app.post("/predict")
def predict(request: RecoveryPredictRequest) -> dict[str, Any]:
    trace_id = f"alg3-{uuid4().hex[:12]}"
    if regression_bundle is None:
        return {
            "traceId": trace_id,
            "status": "FAILED",
            "result": None,
            "errorMessage": "Algorithm3 regression model is not loaded",
        }

    features = _build_features(request)
    regression_features = regression_bundle["features"]
    regression_frame = _feature_frame(features, regression_features)
    predicted_minutes = float(regression_bundle["model"].predict(regression_frame)[0])
    if math.isnan(predicted_minutes) or math.isinf(predicted_minutes):
        predicted_minutes = float(feature_defaults.get("duration", 30.0))
    predicted_minutes = max(1.0, min(predicted_minutes, 1440.0))

    probability = None
    classification_name = None
    threshold_minutes = 30
    if classification_bundle is not None:
        classification_features = classification_bundle["features"]
        classification_frame = _feature_frame(features, classification_features)
        model = classification_bundle["model"]
        classification_name = classification_bundle.get("best_model_name")
        threshold_minutes = int(classification_bundle.get("threshold_minutes", 30))
        if hasattr(model, "predict_proba"):
            probability = float(model.predict_proba(classification_frame)[0][1])
        else:
            probability = float(model.predict(classification_frame)[0])

    duration_minutes = int(round(predicted_minutes))
    band = _duration_band(predicted_minutes)
    confidence = probability if probability is not None else min(0.95, max(0.55, predicted_minutes / 120.0))

    model_version = "algorithm3-recovery"
    if regression_bundle.get("best_model_name"):
        model_version += f"-reg:{regression_bundle['best_model_name']}"
    if classification_name:
        model_version += f"+cls:{classification_name}"

    return {
        "traceId": trace_id,
        "status": "COMPLETED",
        "result": {
            "predictedRecoveryDurationMinutes": duration_minutes,
            "recoveryLevel": band,
            "longRecoveryProbability": round(float(probability), 4) if probability is not None else None,
            "classificationThresholdMinutes": threshold_minutes,
            "confidence": round(float(confidence), 4),
            "modelVersion": model_version,
            "recommendation": _recommendation(band, request, duration_minutes),
            "keyFactors": _key_factors(features),
            "metrics": regression_bundle.get("metrics"),
            "features": {
                name: float(regression_frame.iloc[0][name])
                for name in regression_features
            },
        },
        "errorMessage": None,
    }


@app.post("/api/recovery/recommend")
def recommend(request: RecoveryPredictRequest) -> dict[str, Any]:
    return predict(request)
