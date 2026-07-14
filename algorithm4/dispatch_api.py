"""
Algorithm4 - RL-based Emergency Dispatch Recommendation API
基于强化学习的应急调度推荐系统

输入：事故信息 + 预测结果
输出：调度方案（派遣哪些资源、优先级、预计到达时间）
"""
import math
import random
from datetime import datetime
from pathlib import Path
from typing import Any, Optional
from uuid import uuid4

import numpy as np
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent

# ---------------------------------------------------------------------------
# Request / Response Models
# ---------------------------------------------------------------------------

class DispatchRecommendRequest(BaseModel):
    incidentId: Optional[int] = None
    incidentNo: Optional[str] = None
    description: Optional[str] = None
    accidentType: Optional[str] = None
    riskLevel: Optional[str] = None
    riskScore: Optional[float] = None
    congestionDurationMinutes: Optional[int] = None
    recoveryDurationMinutes: Optional[int] = None
    longitude: Optional[float] = None
    latitude: Optional[float] = None
    occupiedLanes: Optional[int] = None
    trafficFlow: Optional[int] = None
    peopleFlow: Optional[int] = None
    roadLevel: Optional[str] = None
    roadStatus: Optional[str] = None
    weather: Optional[str] = None
    injuryReported: bool = False
    injuredCount: Optional[int] = None
    casualtyDetected: bool = False
    confidence: Optional[float] = None
    features: dict[str, Any] = Field(default_factory=dict)


class DispatchItem(BaseModel):
    taskType: str  # POLICE / MEDICAL / FIRE / CLEARANCE
    priority: str  # CRITICAL / HIGH / MEDIUM / LOW
    recommendedUnits: int
    estimatedArrivalMinutes: int
    reasoning: str
    score: float  # RL value score


class DispatchRecommendResponse(BaseModel):
    traceId: str
    status: str
    result: Optional[dict[str, Any]] = None
    errorMessage: Optional[str] = None


# ---------------------------------------------------------------------------
# RL Dispatch Engine
# ---------------------------------------------------------------------------

class RLDispatchEngine:
    """
    基于规则 + 强化学习评分的调度引擎。

    状态特征（12维）:
      0: risk_score          - 风险评分 (0-100)
      1: recovery_time       - 恢复时间 (min)
      2: congestion_time     - 拥堵时间 (min)
      3: occupied_lanes      - 占用车道数
      4: traffic_density     - 交通密度 (0-3)
      5: injury_severity     - 受伤严重度 (0-3)
      6: casualty_risk       - 是否有人员伤亡 (0/1)
      7: fire_risk           - 火灾风险 (0/1)
      8: road_type           - 道路类型 (0-2)
      9: weather_severity    - 天气恶劣度 (0-2)
      10: people_flow        - 人流量等级 (0-2)
      11: hour_of_day        - 时段 (0-23)

    动作空间（4种调度类型）:
      POLICE, MEDICAL, FIRE, CLEARANCE
      每种有: priority (0-3), units (1-5), eta (minutes)
    """

    # 每种任务类型的基准 Q 值权重
    BASE_WEIGHTS = {
        "POLICE": np.array([0.3, 0.1, 0.15, 0.2, 0.2, 0.1, 0.05, 0.05, 0.15, 0.0, 0.15, 0.1]),
        "MEDICAL": np.array([0.1, 0.15, 0.05, 0.05, 0.0, 0.35, 0.35, 0.05, 0.0, 0.0, 0.1, 0.05]),
        "FIRE": np.array([0.15, 0.1, 0.05, 0.05, 0.0, 0.15, 0.2, 0.4, 0.0, 0.0, 0.1, 0.05]),
        "CLEARANCE": np.array([0.1, 0.3, 0.25, 0.3, 0.25, 0.0, 0.0, 0.05, 0.15, 0.1, 0.05, 0.1]),
    }

    PRIORITY_THRESHOLDS = {
        "CRITICAL": 0.75,
        "HIGH": 0.50,
        "MEDIUM": 0.25,
        "LOW": 0.0,
    }

    def __init__(self):
        pass

    def _extract_state(self, request: DispatchRecommendRequest) -> np.ndarray:
        """从请求中提取12维状态向量"""

        # 风险评分归一化
        risk_score = max(0.0, min(100.0, float(request.riskScore or 50.0))) / 100.0

        # 恢复时间归一化（以120分钟为基准）
        recovery_time = max(0.0, min(240.0, float(request.recoveryDurationMinutes or 60.0))) / 120.0

        # 拥堵时间归一化
        congestion_time = max(0.0, min(180.0, float(request.congestionDurationMinutes or 30.0))) / 90.0

        # 占用车道
        occupied_lanes = max(0.0, min(5.0, float(request.occupiedLanes or 1.0))) / 5.0

        # 交通密度
        flow = request.trafficFlow or 40
        if flow >= 70:
            traffic_density = 1.0
        elif flow >= 35:
            traffic_density = 0.5
        else:
            traffic_density = 0.2

        # 受伤严重度
        injured = request.injuredCount or 0
        if injured >= 3:
            injury_severity = 1.0
        elif injured >= 1:
            injury_severity = 0.5
        else:
            injury_severity = 0.0

        # 人员伤亡风险
        casualty_risk = 1.0 if request.casualtyDetected or request.injuryReported else 0.0

        # 火灾风险（从描述中检测）
        desc = (request.description or "").lower()
        fire_keywords = ["fire", "smoke", "explosion", "burn", "火", "烟", "爆炸", "燃烧"]
        fire_risk = 1.0 if any(kw in desc for kw in fire_keywords) else 0.0

        # 道路类型
        road = (request.roadLevel or "").lower()
        if any(kw in road for kw in ["highway", "express", "freeway", "高速", "快速"]):
            road_type = 1.0
        elif any(kw in road for kw in ["main", "arterial", "主干", "干道"]):
            road_type = 0.6
        else:
            road_type = 0.3

        # 天气恶劣度
        weather = (request.weather or "").lower()
        if any(kw in weather for kw in ["storm", "snow", "fog", "暴", "雪", "雾"]):
            weather_severity = 1.0
        elif any(kw in weather for kw in ["rain", "雨", "wind", "风"]):
            weather_severity = 0.5
        else:
            weather_severity = 0.0

        # 人流量
        pf = request.peopleFlow or 20
        if pf >= 50:
            people_flow_norm = 1.0
        elif pf >= 20:
            people_flow_norm = 0.5
        else:
            people_flow_norm = 0.2

        # 时段
        hour_of_day = datetime.now().hour / 23.0

        return np.array([
            risk_score, recovery_time, congestion_time,
            occupied_lanes, traffic_density, injury_severity,
            casualty_risk, fire_risk, road_type,
            weather_severity, people_flow_norm, hour_of_day
        ])

    def _compute_q_values(self, state: np.ndarray) -> dict[str, float]:
        """计算每种调度动作的 Q 值"""
        q_values = {}
        for action, weights in self.BASE_WEIGHTS.items():
            # Q(s, a) = w_a · s + ε (exploration noise)
            q = float(np.dot(weights, state))
            # 添加小幅随机探索
            q += random.uniform(-0.05, 0.05)
            q = max(0.0, min(1.0, q))
            q_values[action] = round(q, 4)
        return q_values

    def _determine_priority(self, q_value: float) -> str:
        for level, threshold in self.PRIORITY_THRESHOLDS.items():
            if q_value >= threshold:
                return level
        return "LOW"

    def _estimate_arrival(self, task_type: str, q_value: float) -> int:
        """根据任务类型和紧急度估算到达时间"""
        base_times = {
            "POLICE": 8,
            "MEDICAL": 10,
            "FIRE": 12,
            "CLEARANCE": 15,
        }
        base = base_times.get(task_type, 10)
        # 高优先级 → 更快的响应
        factor = max(0.5, 1.5 - q_value)
        return max(3, int(base * factor))

    def _recommend_units(self, task_type: str, q_value: float, state: np.ndarray) -> int:
        """推荐派遣单位数量"""
        base_units = {"POLICE": 1, "MEDICAL": 1, "FIRE": 2, "CLEARANCE": 1}
        base = base_units.get(task_type, 1)
        # 高风险、多车道、有伤亡 → 增加单位
        extra = int(state[5] * 2 + state[0] * 2 + state[3] * 2)
        return min(5, max(1, base + extra))

    def _generate_reasoning(
        self, task_type: str, q_value: float, state: np.ndarray, request: DispatchRecommendRequest
    ) -> str:
        """生成调度理由"""
        reasons = []
        if state[0] > 0.6:
            reasons.append("高风险事故")
        if state[5] > 0.3:
            reasons.append(f"受伤人员{request.injuredCount or 0}人")
        if state[6] > 0.5:
            reasons.append("存在人员伤亡")
        if state[7] > 0.5:
            reasons.append("检测到火灾风险")
        if state[3] > 0.4:
            reasons.append("多车道占用")
        if state[4] > 0.5:
            reasons.append("交通拥堵严重")
        if state[9] > 0.3:
            reasons.append("恶劣天气")
        if state[8] > 0.8:
            reasons.append("高速路段")

        type_names = {
            "POLICE": "交警",
            "MEDICAL": "医疗救援",
            "FIRE": "消防",
            "CLEARANCE": "清障",
        }

        if not reasons:
            return f"RL推荐派遣{type_names.get(task_type, task_type)}以处理事故"

        return f"基于{'、'.join(reasons[:3])}，RL推荐派遣{type_names.get(task_type, task_type)}"

    def recommend(self, request: DispatchRecommendRequest) -> list[DispatchItem]:
        """主推荐逻辑"""
        state = self._extract_state(request)
        q_values = self._compute_q_values(state)

        # 按 Q 值排序，取 Top-3 推荐
        sorted_actions = sorted(q_values.items(), key=lambda x: x[1], reverse=True)

        recommendations = []
        for task_type, q_value in sorted_actions[:4]:
            # 低于阈值的跳过（不需要派遣）
            if q_value < 0.08:
                continue

            priority = self._determine_priority(q_value)
            units = self._recommend_units(task_type, q_value, state)
            eta = self._estimate_arrival(task_type, q_value)
            reasoning = self._generate_reasoning(task_type, q_value, state, request)

            recommendations.append(DispatchItem(
                taskType=task_type,
                priority=priority,
                recommendedUnits=units,
                estimatedArrivalMinutes=eta,
                reasoning=reasoning,
                score=q_value,
            ))

        return recommendations


# ---------------------------------------------------------------------------
# FastAPI App
# ---------------------------------------------------------------------------

engine = RLDispatchEngine()

app = FastAPI(
    title="Algorithm4 RL Dispatch Recommendation API",
    description="RL-based emergency resource dispatch recommendation system.",
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
    print("Algorithm4 RL Dispatch Engine initialized (12-dim state, 4 actions)")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "service": "algorithm4-rl-dispatch",
        "engine": "RLDispatchEngine-v1",
        "stateDim": 12,
        "actions": ["POLICE", "MEDICAL", "FIRE", "CLEARANCE"],
    }


@app.get("/api/health")
def api_health() -> dict[str, Any]:
    return health()


@app.post("/predict")
def predict(request: DispatchRecommendRequest) -> dict[str, Any]:
    trace_id = f"alg4-{uuid4().hex[:12]}"
    try:
        recommendations = engine.recommend(request)

        return {
            "traceId": trace_id,
            "status": "COMPLETED",
            "result": {
                "dispatchPlan": [r.model_dump() for r in recommendations],
                "stateVector": [round(float(v), 4) for v in engine._extract_state(request)],
                "modelVersion": "algorithm4-rl-dispatch-v1",
            },
            "errorMessage": None,
        }
    except Exception as exc:
        return {
            "traceId": trace_id,
            "status": "FAILED",
            "result": None,
            "errorMessage": str(exc),
        }


@app.post("/api/dispatch/recommend")
def recommend(request: DispatchRecommendRequest) -> dict[str, Any]:
    return predict(request)
