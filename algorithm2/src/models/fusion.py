"""
专家融合策略模块
将各个专家的输出进行加权融合，并应用高风险保护规则。
"""
import logging
from typing import Optional, List

from src.config import (
    FUSION_WEIGHTS,
    FUSION_HIGH_RISK_MIN,
    FUSION_CRITICAL_MIN,
    FUSION_LETHAL_MIN,
    FUSION_MIN_SCORE,
    FUSION_MAX_SCORE,
    FINAL_LEVEL_THRESHOLDS,
)

logger = logging.getLogger(__name__)

# 被视为高风险的等级
HIGH_RISK_LEVELS = {"high", "critical"}
CRITICAL_LEVELS = {"critical"}

# 致命关键词（触发 lethal 保护）
LETHAL_KEYWORDS = {"死亡", "昏迷", "夹困", "起火", "爆炸", "危化品"}


def fuse_experts(
    injury_result: dict,
    traffic_result: dict,
    vision_result: Optional[dict],
    text_result: dict,
) -> dict:
    """
    融合四个专家的输出，计算最终风险等级。

    Args:
        injury_result: 人员伤害专家输出
        traffic_result: 交通影响专家输出
        vision_result: 视觉专家输出（可能为 None）
        text_result: 文字专家输出

    Returns:
        dict: 融合后的完整预测结果
    """
    # 收集有效专家的分数用于加权
    injury_score = injury_result.get("risk_score", 2.0)
    traffic_score = traffic_result.get("risk_score", 2.0)
    vision_score = vision_result.get("risk_score", 0) if vision_result else 0
    text_bonus = text_result.get("text_bonus", 0.0)

    # 收集各专家的风险等级
    injury_level = injury_result.get("risk_level", "medium")
    traffic_level = traffic_result.get("risk_level", "medium")
    vision_level = vision_result.get("risk_level") if vision_result else None

    all_levels: List[str] = [injury_level, traffic_level]
    if vision_level:
        all_levels.append(vision_level)

    # 计算基础加权分数
    has_vision = vision_result is not None

    if has_vision:
        # 三个专家都参与
        total_weight = FUSION_WEIGHTS["injury"] + FUSION_WEIGHTS["traffic"] + FUSION_WEIGHTS["vision"]
        base_score = (
            FUSION_WEIGHTS["injury"] * injury_score
            + FUSION_WEIGHTS["traffic"] * traffic_score
            + FUSION_WEIGHTS["vision"] * vision_score
        ) / total_weight + text_bonus
    else:
        # 只有 injury 和 traffic 两个专家
        total_weight = FUSION_WEIGHTS["injury"] + FUSION_WEIGHTS["traffic"]
        base_score = (
            FUSION_WEIGHTS["injury"] * injury_score
            + FUSION_WEIGHTS["traffic"] * traffic_score
        ) / total_weight + text_bonus

    logger.info(f"基础加权分数: {base_score:.4f}")

    # --- 高风险保护规则 ---

    final_score = base_score

    # 规则 1: 任一专家输出 high 或 critical
    has_high_or_critical = any(level in HIGH_RISK_LEVELS for level in all_levels)
    if has_high_or_critical:
        final_score = max(final_score, FUSION_HIGH_RISK_MIN)
        logger.info(f"触发高风险保护: final_score >= {FUSION_HIGH_RISK_MIN}")

    # 规则 2: 任一专家输出 critical
    has_critical = any(level in CRITICAL_LEVELS for level in all_levels)
    if has_critical:
        final_score = max(final_score, FUSION_CRITICAL_MIN)
        logger.info(f"触发 critical 保护: final_score >= {FUSION_CRITICAL_MIN}")

    # 规则 3: 文本包含致命关键词
    matched_keywords = set(text_result.get("matched_keywords", []))
    if matched_keywords & LETHAL_KEYWORDS:
        final_score = max(final_score, FUSION_LETHAL_MIN)
        logger.info(f"触发致命关键词保护: final_score >= {FUSION_LETHAL_MIN}")

    # 规则 4: 限制在 [1, 4]
    final_score = max(FUSION_MIN_SCORE, min(FUSION_MAX_SCORE, final_score))
    final_score = round(final_score, 4)

    # 最终等级映射。对外输出只保留 low / medium / high 三类。
    final_level = "high"
    for threshold, level in FINAL_LEVEL_THRESHOLDS:
        if final_score < threshold:
            final_level = level
            break
    if final_level == "critical":
        final_level = "high"

    logger.info(f"最终融合: level={final_level}, score={final_score}")

    # 构建响应
    experts = {
        "injury": {
            "risk_level": injury_level,
            "risk_score": injury_score,
            "confidence": injury_result.get("confidence"),
            "probabilities": injury_result.get("probabilities", {}),
        },
        "traffic": {
            "risk_level": traffic_level,
            "risk_score": traffic_score,
            "confidence": traffic_result.get("confidence"),
            "probabilities": traffic_result.get("probabilities", {}),
        },
        "text": {
            "text_bonus": text_bonus,
            "matched_keywords": list(matched_keywords),
        },
    }

    if vision_result:
        experts["vision"] = {
            "risk_level": vision_level,
            "risk_score": vision_score,
            "confidence": vision_result.get("confidence"),
        }

    return {
        "final_risk_level": final_level,
        "final_risk_score": final_score,
        "fusion_strategy": "weighted_sum_with_high_risk_protection",
        "experts": experts,
    }
