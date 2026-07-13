"""
视觉专家模块
"""
import logging
from typing import Optional, Tuple

from src.config import (
    VISION_MODEL_PATH,
    VISION_LABEL_MAPPING,
    VISION_SCORE_MAPPING,
)

logger = logging.getLogger(__name__)

# 全局模型实例，服务启动时加载一次
_vision_model = None


def load_vision_model():
    """
    加载视觉专家模型 (best.pt)。
    在服务启动时调用一次。
    """
    global _vision_model
    if _vision_model is not None:
        return _vision_model

    try:
        from ultralytics import YOLO
    except ImportError:
        raise ImportError(
            "请安装 ultralytics: pip install ultralytics"
        )

    logger.info(f"加载视觉专家模型: {VISION_MODEL_PATH}")
    _vision_model = YOLO(VISION_MODEL_PATH)
    logger.info("视觉专家模型加载完成")

    # 打印模型类别信息
    if hasattr(_vision_model, "names"):
        logger.info(f"视觉模型类别映射: {_vision_model.names}")

    return _vision_model


def _map_label(raw_label: str) -> str:
    """将模型原始标签映射为标准标签 (low/medium/high)"""
    # 先尝试直接匹配
    if raw_label in VISION_SCORE_MAPPING:
        return raw_label
    # 尝试通过映射字典转换
    if raw_label in VISION_LABEL_MAPPING:
        return VISION_LABEL_MAPPING[raw_label]
    # 尝试大小写不敏感匹配
    lowered = raw_label.lower()
    if lowered in VISION_SCORE_MAPPING:
        return lowered
    for k, v in VISION_LABEL_MAPPING.items():
        if k.lower() == lowered:
            return v
    # 无法映射则返回原始值
    logger.warning(f"无法映射视觉标签 '{raw_label}'，使用原始值")
    return raw_label


def predict_vision(image_bytes: bytes) -> dict:
    """
    使用视觉专家模型对事故图像进行推理。
    """
    model = load_vision_model()

    # 将 bytes 写入临时文件供 YOLO 推理
    import tempfile
    import os

    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as f:
        f.write(image_bytes)
        tmp_path = f.name

    try:
        results = model(tmp_path, verbose=False)
    finally:
        os.unlink(tmp_path)

    if not results or len(results) == 0:
        raise RuntimeError("视觉模型推理未返回结果")

    result = results[0]

    # 如果有检测结果，取置信度最高的类别
    if result.probs is not None:
        # 分类模型
        probs = result.probs
        top_idx = probs.top1
        confidence = float(probs.top1conf)
        raw_label = result.names[top_idx] if hasattr(result, "names") else str(top_idx)

        # 获取所有类别的概率
        all_probs = {}
        for i, prob in enumerate(probs.data):
            label_i = result.names[i] if hasattr(result, "names") else str(i)
            all_probs[label_i] = float(prob)
    elif result.boxes is not None and len(result.boxes) > 0:
        # 检测模型，取最高置信度检测的类别
        boxes = result.boxes
        best_idx = boxes.conf.argmax()
        confidence = float(boxes.conf[best_idx])
        cls_id = int(boxes.cls[best_idx])
        raw_label = result.names[cls_id] if hasattr(result, "names") else str(cls_id)
        all_probs = {}
    else:
        # 无检测结果，默认 low
        logger.warning("视觉模型未检测到目标，默认返回 low")
        return {
            "risk_level": "low",
            "risk_score": VISION_SCORE_MAPPING["low"],
            "confidence": 0.0,
        }

    # 映射标签
    mapped_label = _map_label(raw_label)

    # 获取分数
    risk_score = VISION_SCORE_MAPPING.get(mapped_label)
    if risk_score is None:
        logger.warning(
            f"无法获取标签 '{mapped_label}' 的分数映射，使用默认值 1.0"
        )
        risk_score = 1.0

    return {
        "risk_level": mapped_label,
        "risk_score": risk_score,
        "confidence": round(confidence, 4),
    }
