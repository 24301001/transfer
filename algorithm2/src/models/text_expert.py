"""
文字专家模块
"""
import logging
from typing import List, Tuple

from src.config import (
    TEXT_KEYWORD_RULES,
    TEXT_BONUS_MIN,
    TEXT_BONUS_MAX,
)

logger = logging.getLogger(__name__)


def predict_text(description: str) -> dict:
    """
    基于关键词规则计算 text_bonus。
    """
    if not description or not description.strip():
        logger.info("文字专家: 无文字描述，text_bonus = 0")
        return {
            "text_bonus": 0.0,
            "matched_keywords": [],
        }

    description = description.strip()
    text_bonus = 0.0
    matched_keywords: List[str] = []

    for keywords, bonus in TEXT_KEYWORD_RULES:
        for kw in keywords:
            if kw in description:
                if kw not in matched_keywords:
                    matched_keywords.append(kw)
                    text_bonus += bonus

    # 限制范围
    text_bonus = max(TEXT_BONUS_MIN, min(TEXT_BONUS_MAX, text_bonus))
    text_bonus = round(text_bonus, 4)

    logger.info(
        f"文字专家: text_bonus={text_bonus}, matched={matched_keywords}"
    )

    return {
        "text_bonus": text_bonus,
        "matched_keywords": matched_keywords,
    }
