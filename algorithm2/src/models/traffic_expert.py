#交通影响风险专家模块

import logging
import pickle
from typing import Optional

import numpy as np

from src.config import (
    TRAFFIC_PREPROCESSOR_PATH,
    TRAFFIC_MODEL_PATH,
    TRAFFIC_SCORE_MAPPING,
)

logger = logging.getLogger(__name__)

# 全局模型实例
_traffic_preprocessor = None
_traffic_model = None


def _patch_xgboost_model(model):
    """
    兼容不同版本 xgboost 的 pickle 模型。
    """
    import inspect
    from xgboost import XGBModel

    init_params = set()
    for cls in type(model).__mro__:
        if cls is object:
            break
        try:
            for name, param in inspect.signature(cls.__init__).parameters.items():
                if name != "self" and name != "kwargs":
                    init_params.add(name)
        except (ValueError, TypeError):
            continue

    for param_name in init_params:
        if not hasattr(model, param_name):
            default = getattr(type(model)(), param_name, None)
            try:
                setattr(model, param_name, default)
            except Exception:
                pass

# 输入字段
TRAFFIC_FEATURE_COLUMNS = [
    "Distance(mi)",
    "Temperature(F)",
    "Humidity(%)",
    "Pressure(in)",
    "Visibility(mi)",
    "Wind_Speed(mph)",
    "Precipitation(in)",
    "Weather_Condition",
    "Bump",
    "Crossing",
    "Give_Way",
    "Junction",
    "No_Exit",
    "Railway",
    "Roundabout",
    "Station",
    "Stop",
    "Traffic_Calming",
    "Traffic_Signal",
    "Sunrise_Sunset",
    "Civil_Twilight",
    "accident_hour",
    "weekday",
    "is_peak_hour",
    "is_night",
]

# 模型输出标签顺序
TRAFFIC_LABELS = ["low", "medium", "high", "critical"]


def _fallback_traffic_prediction(structured_data: Optional[dict], reason: Exception) -> dict:
    logger.warning("Traffic model unavailable, using rule fallback: %s", reason)
    structured_data = structured_data or {}
    score = 1.8

    def as_float(name: str, default: float = 0.0) -> float:
        try:
            value = structured_data.get(name, default)
            if value is None or value == "":
                return default
            return float(value)
        except (TypeError, ValueError):
            return default

    text = " ".join(str(v) for v in structured_data.values() if v is not None).lower()
    if as_float("Distance(mi)") >= 2:
        score += 0.5
    if as_float("is_peak_hour") >= 1:
        score += 0.35
    if as_float("Visibility(mi)", 10) <= 3:
        score += 0.45
    if as_float("Precipitation(in)") > 0:
        score += 0.35
    if as_float("Traffic_Signal") >= 1 or as_float("Junction") >= 1:
        score += 0.25
    if any(word in text for word in ("rain", "snow", "fog", "storm", "雨", "雪", "雾", "拥堵", "堵塞")):
        score += 0.45

    if score >= 3.5:
        risk_level = "critical"
        probabilities = {"low": 0.05, "medium": 0.10, "high": 0.25, "critical": 0.60}
    elif score >= 2.6:
        risk_level = "high"
        probabilities = {"low": 0.08, "medium": 0.18, "high": 0.58, "critical": 0.16}
    elif score >= 1.8:
        risk_level = "medium"
        probabilities = {"low": 0.18, "medium": 0.62, "high": 0.15, "critical": 0.05}
    else:
        risk_level = "low"
        probabilities = {"low": 0.72, "medium": 0.18, "high": 0.07, "critical": 0.03}

    return {
        "risk_level": risk_level,
        "risk_score": TRAFFIC_SCORE_MAPPING.get(risk_level, 2),
        "confidence": max(probabilities.values()),
        "probabilities": probabilities,
        "fallback": True,
        "fallback_reason": str(reason),
    }


def load_traffic_model():
    """加载交通影响风险专家模型和预处理器"""
    global _traffic_preprocessor, _traffic_model

    if _traffic_preprocessor is not None and _traffic_model is not None:
        return _traffic_preprocessor, _traffic_model

    logger.info(f"加载交通影响预处理器: {TRAFFIC_PREPROCESSOR_PATH}")
    with open(TRAFFIC_PREPROCESSOR_PATH, "rb") as f:
        _traffic_preprocessor = pickle.load(f)

    logger.info(f"加载交通影响 XGBoost 模型: {TRAFFIC_MODEL_PATH}")
    with open(TRAFFIC_MODEL_PATH, "rb") as f:
        _traffic_model = pickle.load(f)

    _patch_xgboost_model(_traffic_model)

    logger.info("交通影响风险专家模型加载完成")
    return _traffic_preprocessor, _traffic_model


def predict_traffic(structured_data: Optional[dict] = None) -> dict:
    """
    使用交通影响风险专家进行推理。
    """
    try:
        preprocessor, model = load_traffic_model()
    except Exception as exc:
        return _fallback_traffic_prediction(structured_data, exc)

    if structured_data is None:
        structured_data = {}

    # 构建特征行
    # 根据预处理器结构：Bump/Crossing/Junction 等被归入 numeric 管道，需映射为 0/1
    # Weather_Condition/Sunrise_Sunset/Civil_Twilight/is_night 被归入 category 管道
    _true_values = {"True", "true", "TRUE", "1", 1, True}
    _false_values = {"False", "false", "FALSE", "0", 0, False}

    _boolean_numeric_fields = {
        "Bump", "Crossing", "Give_Way", "Junction", "No_Exit",
        "Railway", "Roundabout", "Station", "Stop",
        "Traffic_Calming", "Traffic_Signal",
    }
    _numeric_fields = {
        "Distance(mi)", "Temperature(F)", "Humidity(%)", "Pressure(in)",
        "Visibility(mi)", "Wind_Speed(mph)", "Precipitation(in)",
        "accident_hour", "weekday", "is_peak_hour",
    }
    # is_night 在预处理器中属于 category 管道
    _category_fields = {
        "Weather_Condition", "Sunrise_Sunset", "Civil_Twilight", "is_night",
    }

    feature_row = {}
    for col in TRAFFIC_FEATURE_COLUMNS:
        val = structured_data.get(col)

        if col in _numeric_fields:
            if val is None or val == "":
                val = np.nan
            else:
                try:
                    val = float(val)
                except (ValueError, TypeError):
                    val = np.nan
        elif col in _boolean_numeric_fields:
            if val is None or val == "":
                val = np.nan
            elif isinstance(val, bool):
                val = 1.0 if val else 0.0
            elif isinstance(val, (int, float)):
                val = float(val)
            elif isinstance(val, str):
                if val in _true_values:
                    val = 1.0
                elif val in _false_values:
                    val = 0.0
                else:
                    val = np.nan
            else:
                val = np.nan
        elif col in _category_fields:
            if val is None or (isinstance(val, float) and np.isnan(val)) or val == "":
                val = "Unknown"
            else:
                val = str(val)
        else:
            val = "Unknown"

        feature_row[col] = val

    # 转换为 DataFrame
    import pandas as pd
    df = pd.DataFrame([feature_row])

    # 预处理
    X = preprocessor.transform(df)

    # 推理
    proba = model.predict_proba(X)[0]
    pred_idx = int(np.argmax(proba))
    confidence = float(proba[pred_idx])

    # 标签映射
    risk_level = TRAFFIC_LABELS[pred_idx] if pred_idx < len(TRAFFIC_LABELS) else "medium"
    risk_score = TRAFFIC_SCORE_MAPPING.get(risk_level, 2)

    # 构建概率字典
    probabilities = {}
    for i, label in enumerate(TRAFFIC_LABELS):
        if i < len(proba):
            probabilities[label] = round(float(proba[i]), 4)

    logger.info(
        f"交通影响专家: level={risk_level}, score={risk_score}, confidence={confidence:.4f}"
    )

    return {
        "risk_level": risk_level,
        "risk_score": risk_score,
        "confidence": round(confidence, 4),
        "probabilities": probabilities,
    }
