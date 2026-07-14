from __future__ import annotations

from pathlib import Path


PACKAGE_ROOT = Path(__file__).resolve().parent
DATA_DIR = PACKAGE_ROOT / "data"
MODEL_DIR = PACKAGE_ROOT / "models"

# 原始 CSV 数据路径（仅预处理时需要，部署时不需要）
# 可通过环境变量或命令行参数覆盖
EMS_RAW_DEFAULT = Path(
    __import__("os").environ.get("EMS_RAW_PATH", "")
) if __import__("os").environ.get("EMS_RAW_PATH") else None
FIRE_RAW_DEFAULT = Path(
    __import__("os").environ.get("FIRE_RAW_PATH", "")
) if __import__("os").environ.get("FIRE_RAW_PATH") else None

EMS_PROCESSED = DATA_DIR / "ems_processed.csv"
FIRE_PROCESSED = DATA_DIR / "fire_processed.csv"

EMS_MODEL = MODEL_DIR / "ems_ppo.zip"
FIRE_MODEL = MODEL_DIR / "fire_ppo.zip"

