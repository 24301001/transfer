import os

# 项目根目录
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(BASE_DIR, "model")

# 模型文件路径
VISION_MODEL_PATH = os.path.join(MODEL_DIR, "best.pt")
INJURY_PREPROCESSOR_PATH = os.path.join(MODEL_DIR, "preprocessor.pkl")
INJURY_MODEL_PATH = os.path.join(MODEL_DIR, "xgboost_injury_model.pkl")
TRAFFIC_PREPROCESSOR_PATH = os.path.join(MODEL_DIR, "preprocessor_us_traffic.pkl")
TRAFFIC_MODEL_PATH = os.path.join(MODEL_DIR, "xgboost_us_traffic_impact.pkl")

# 视觉专家类别映射（可修改）
# 如果 best.pt 输出的类别名不是 low/medium/high，在这里映射
# best.pt 实际输出: {0: 'accident', 1: 'moderate', 2: 'severe'}
VISION_LABEL_MAPPING = {
    "accident": "low",
    "moderate": "medium",
    "severe": "high",
    "minor": "low",
}

# 视觉专家分数映射
VISION_SCORE_MAPPING = {
    "low": 1.0,
    "medium": 2.5,
    "high": 4.0,
}

# 人员伤害风险分数映射
INJURY_SCORE_MAPPING = {
    "medium": 2,
    "high": 3,
    "critical": 4,
}

# 交通影响风险分数映射
TRAFFIC_SCORE_MAPPING = {
    "low": 1,
    "medium": 2,
    "high": 3,
    "critical": 4,
}

# 文字专家关键词规则
TEXT_KEYWORD_RULES = [
    (["死亡", "无意识", "昏迷", "夹困", "起火", "爆炸", "危化品"], 0.8),
    (["有人受伤", "流血", "骨折", "救护车", "消防", "多车追尾"], 0.5),
    (["道路封闭", "严重拥堵", "高速", "隧道", "桥梁"], 0.4),
    (["轻微剐蹭", "无人受伤", "已靠边", "可通行"], -0.3),
]

# text_bonus 范围
TEXT_BONUS_MIN = -0.5
TEXT_BONUS_MAX = 1.0

# 融合权重
FUSION_WEIGHTS = {
    "injury": 0.30,
    "traffic": 0.30,
    "vision": 0.25,
}

# 融合策略中的高风险保护规则
FUSION_HIGH_RISK_MIN = 3.0   # 任一专家 high/critical 时最低分
FUSION_CRITICAL_MIN = 3.5    # 任一专家 critical 时最低分
FUSION_LETHAL_MIN = 3.6      # 文本含致命关键词时最低分
FUSION_MIN_SCORE = 1.0
FUSION_MAX_SCORE = 4.0

# 最终等级映射
FINAL_LEVEL_THRESHOLDS = [
    (1.8, "low"),
    (2.6, "medium"),
    (3.4, "high"),
    (float("inf"), "critical"),
]

# 服务配置
HOST = os.environ.get("HOST", "0.0.0.0")
PORT = int(os.environ.get("PORT", 8001))
PRELOAD_MODELS = os.environ.get("PRELOAD_MODELS", "false").lower() in {"1", "true", "yes"}
