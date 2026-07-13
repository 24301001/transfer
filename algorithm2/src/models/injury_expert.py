#人员伤害风险专家模块

import logging
import pickle
from typing import Optional

import numpy as np

from src.config import (
    INJURY_PREPROCESSOR_PATH,
    INJURY_MODEL_PATH,
    INJURY_SCORE_MAPPING,
)

logger = logging.getLogger(__name__)

# 全局模型实例
_injury_preprocessor = None
_injury_model = None


def _patch_xgboost_model(model):
    """
    兼容不同版本 xgboost 的 pickle 模型。
    """
    import inspect
    from xgboost import XGBModel

    # 收集参数名
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
INJURY_FEATURE_COLUMNS = [
    "Lanes_or_Medians",
    "Types_of_Junction",
    "Road_surface_type",
    "Light_conditions",
    "Weather_conditions",
    "Type_of_collision",
    "Vehicle_movement",
    "Pedestrian_movement",
]

# 模型输出标签顺序
INJURY_LABELS = ["medium", "high", "critical"]


def _fallback_injury_prediction(structured_data: Optional[dict], reason: Exception) -> dict:
    logger.warning("Injury model unavailable, using rule fallback: %s", reason)
    structured_data = structured_data or {}
    text = " ".join(str(v) for v in structured_data.values() if v is not None).lower()
    score = 2.0

    if any(word in text for word in ("fatal", "death", "dead", "critical", "死亡", "昏迷", "重伤")):
        score = 4.0
    elif any(word in text for word in ("injury", "injured", "hurt", "受伤", "伤者")):
        score = 3.0
    elif any(word in text for word in ("pedestrian", "行人", "非机动车", "电动车", "摩托")):
        score = max(score, 3.0)

    if score >= 3.5:
        risk_level = "critical"
        probabilities = {"medium": 0.08, "high": 0.22, "critical": 0.70}
    elif score >= 2.5:
        risk_level = "high"
        probabilities = {"medium": 0.16, "high": 0.68, "critical": 0.16}
    else:
        risk_level = "medium"
        probabilities = {"medium": 0.72, "high": 0.20, "critical": 0.08}

    return {
        "risk_level": risk_level,
        "risk_score": INJURY_SCORE_MAPPING.get(risk_level, 2),
        "confidence": max(probabilities.values()),
        "probabilities": probabilities,
        "fallback": True,
        "fallback_reason": str(reason),
    }


def load_injury_model():
    """加载人员伤害风险专家模型和预处理器"""
    global _injury_preprocessor, _injury_model

    if _injury_preprocessor is not None and _injury_model is not None:
        return _injury_preprocessor, _injury_model

    logger.info(f"加载人员伤害预处理器: {INJURY_PREPROCESSOR_PATH}")
    with open(INJURY_PREPROCESSOR_PATH, "rb") as f:
        _injury_preprocessor = pickle.load(f)

    logger.info(f"加载人员伤害 XGBoost 模型: {INJURY_MODEL_PATH}")
    with open(INJURY_MODEL_PATH, "rb") as f:
        _injury_model = pickle.load(f)

    _patch_xgboost_model(_injury_model)

    logger.info("人员伤害风险专家模型加载完成")
    return _injury_preprocessor, _injury_model


def predict_injury(structured_data: Optional[dict] = None) -> dict:
    """
    使用人员伤害风险专家进行推理。
    """
    try:
        preprocessor, model = load_injury_model()
    except Exception as exc:
        return _fallback_injury_prediction(structured_data, exc)

    if structured_data is None:
        structured_data = {}

    # 构建特征行，缺失字段填 "Unknown"
    feature_row = {}
    for col in INJURY_FEATURE_COLUMNS:
        val = structured_data.get(col)
        if val is None or (isinstance(val, float) and np.isnan(val)) or val == "":
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
    risk_level = INJURY_LABELS[pred_idx] if pred_idx < len(INJURY_LABELS) else "medium"
    risk_score = INJURY_SCORE_MAPPING.get(risk_level, 2)

    # 构建概率字典
    probabilities = {}
    for i, label in enumerate(INJURY_LABELS):
        if i < len(proba):
            probabilities[label] = round(float(proba[i]), 4)

    logger.info(
        f"人员伤害专家: level={risk_level}, score={risk_score}, confidence={confidence:.4f}"
    )

    return {
        "risk_level": risk_level,
        "risk_score": risk_score,
        "confidence": round(confidence, 4),
        "probabilities": probabilities,
    }
