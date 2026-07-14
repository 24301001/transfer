"""
算法4 - 应急资源调度推荐 API
=============================
FastAPI 服务，接收事故特征（起火、翻车、车辆数、受伤风险等），
调用 PPO 强化学习模型 + 规则引擎，输出调度建议。

启动方式:
    uvicorn traffic_dispatch_rl.api:app --host 0.0.0.0 --port 8004

或者从 Gymnasium-main 目录:
    cd Gymnasium-main && uvicorn traffic_dispatch_rl.api:app --host 0.0.0.0 --port 8004
"""

from __future__ import annotations

import logging
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ---- 日志 ----
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


# ============================================================
# Pydantic 模型
# ============================================================

class DispatchRequest(BaseModel):
    """调度推荐请求 - 由算法2/3 的输出字段驱动。"""
    fire: int = Field(
        default=0, ge=0, le=1,
        description="YOLO 是否检测到起火 (0/1)",
    )
    injury_risk: str = Field(
        default="medium",
        pattern="^(low|medium|high)$",
        description="人员伤害风险等级 (low/medium/high)",
    )
    car_flip: int = Field(
        default=0, ge=0, le=1,
        description="YOLO 是否检测到翻车 (0/1)",
    )
    vehicle_num: int = Field(
        default=1, ge=0,
        description="YOLO 检测到的涉事车辆数量",
    )
    affected_lanes: int = Field(
        default=0, ge=0,
        description="受影响车道数",
    )
    lane_status: int = Field(
        default=0, ge=0, le=2,
        description="车道状态: 0=无影响, 1=部分占用, 2=完全封闭",
    )


class ResourceDetail(BaseModel):
    """单个资源的调度详情。"""
    resource: str
    action_name: Optional[str] = None
    ambulance: Optional[int] = None
    firetruck: Optional[int] = None
    ladder: Optional[int] = None
    support_unit: Optional[int] = None
    estimated_response_minutes: Optional[float] = None
    resource_cost: Optional[float] = None
    note: Optional[str] = None


class DispatchResponse(BaseModel):
    """调度推荐响应。"""
    ambulance: int = 0
    firetruck: int = 0
    towtruck: int = 0
    heavy_towtruck: int = 0
    details: list[ResourceDetail] = []


class HealthResponse(BaseModel):
    """健康检查响应。"""
    status: str
    ems_model_loaded: bool
    fire_model_loaded: bool
    version: str


# ============================================================
# 模型加载（启动时）
# ============================================================

_ems_model_loaded = False
_fire_model_loaded = False


def _check_model_files() -> dict[str, bool]:
    """检查模型文件是否存在。"""
    from traffic_dispatch_rl.config import EMS_MODEL, FIRE_MODEL
    return {
        "ems": EMS_MODEL.exists(),
        "fire": FIRE_MODEL.exists(),
    }


@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务生命周期：启动时校验模型文件。"""
    global _ems_model_loaded, _fire_model_loaded
    logger.info("=" * 50)
    logger.info("正在启动应急调度推荐服务 (算法4)...")

    available = _check_model_files()
    _ems_model_loaded = available["ems"]
    _fire_model_loaded = available["fire"]

    if _ems_model_loaded:
        logger.info("✅ EMS PPO 模型已就绪: ems_ppo.zip")
    else:
        logger.warning("⚠️  EMS PPO 模型未找到，将使用 fallback 规则")

    if _fire_model_loaded:
        logger.info("✅ Fire PPO 模型已就绪: fire_ppo.zip")
    else:
        logger.warning("⚠️  Fire PPO 模型未找到，将使用 fallback 规则")

    logger.info("✅ 拖车规则引擎已就绪（规则驱动）")
    logger.info("服务启动完成，监听端口 8004")
    logger.info("=" * 50)

    yield
    logger.info("应急调度推荐服务正在关闭...")


# ============================================================
# FastAPI 应用
# ============================================================

app = FastAPI(
    title="应急资源调度推荐服务 (算法4)",
    description="基于 PPO 强化学习 + 规则引擎的事故应急资源调度推荐 API",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================
# 核心推理函数
# ============================================================

def _predict_ems(injury_risk: str) -> tuple[int, ResourceDetail]:
    """EMS 救护车调度预测。"""
    from traffic_dispatch_rl.config import EMS_MODEL
    from traffic_dispatch_rl.envs import EMSDispatchEnv

    try:
        from stable_baselines3 import PPO
        env = EMSDispatchEnv()
        model = PPO.load(str(EMS_MODEL), env=env)
        obs, _ = env.reset()
        action, _ = model.predict(obs, deterministic=True)
        _, _, _, _, info = env.step(int(action))
        return info.get("ambulance", 0), ResourceDetail(**info)
    except Exception as exc:
        logger.warning("EMS PPO 推理失败，使用 fallback: %s", exc)
        if injury_risk == "high":
            return 2, ResourceDetail(
                resource="ems",
                action_name="fallback_two_ambulances",
                ambulance=2,
            )
        else:
            return 1, ResourceDetail(
                resource="ems",
                action_name="fallback_one_ambulance",
                ambulance=1,
            )


def _predict_fire(fire_detected: int) -> tuple[int, ResourceDetail]:
    """Fire 消防车调度预测。"""
    from traffic_dispatch_rl.config import FIRE_MODEL
    from traffic_dispatch_rl.envs import FireDispatchEnv

    if not fire_detected:
        return 0, ResourceDetail(
            resource="fire",
            action_name="no_fire_detected",
            firetruck=0,
        )

    try:
        from stable_baselines3 import PPO
        env = FireDispatchEnv()
        model = PPO.load(str(FIRE_MODEL), env=env)
        obs, _ = env.reset()
        action, _ = model.predict(obs, deterministic=True)
        _, _, _, _, info = env.step(int(action))
        return info.get("firetruck", 0), ResourceDetail(**info)
    except Exception as exc:
        logger.warning("Fire PPO 推理失败，使用 fallback: %s", exc)
        return 1, ResourceDetail(
            resource="fire",
            action_name="fallback_one_firetruck",
            firetruck=1,
        )


def _predict_tow(
    car_flip: int,
    vehicle_num: int,
    affected_lanes: int,
    lane_status: int,
) -> tuple[int, int, ResourceDetail]:
    """拖车/清障车规则推荐。"""
    from traffic_dispatch_rl.rules import tow_recommendation

    plan = tow_recommendation(
        car_flip=car_flip,
        vehicle_num=vehicle_num,
        affected_lanes=affected_lanes,
        lane_status=lane_status,
    )
    return plan.towtruck, plan.heavy_towtruck, ResourceDetail(
        resource="tow",
        note=plan.note,
    )


# ============================================================
# API 路由
# ============================================================

@app.get("/", response_model=HealthResponse)
async def root():
    """服务根路径 - 健康检查。"""
    return HealthResponse(
        status="running",
        ems_model_loaded=_ems_model_loaded,
        fire_model_loaded=_fire_model_loaded,
        version="1.0.0",
    )


@app.get("/api/health", response_model=HealthResponse)
async def health_check():
    """健康检查接口。"""
    return HealthResponse(
        status="ok",
        ems_model_loaded=_ems_model_loaded,
        fire_model_loaded=_fire_model_loaded,
        version="1.0.0",
    )


@app.post("/dispatch/recommend", response_model=DispatchResponse)
async def recommend(req: DispatchRequest):
    """
    应急资源调度推荐接口。

    输入：事故特征（由 YOLO + 风险模型提供）
    输出：救护车、消防车、拖车、大型拖车数量及调度详情

    示例请求:
    ```json
    {
      "fire": 1,
      "injury_risk": "high",
      "car_flip": 1,
      "vehicle_num": 5,
      "affected_lanes": 2,
      "lane_status": 1
    }
    ```
    """
    details: list[ResourceDetail] = []
    ambulance = 0
    firetruck = 0

    # 1. EMS 救护车
    if req.injury_risk in ("medium", "high"):
        ambulance, ems_detail = _predict_ems(req.injury_risk)
        details.append(ems_detail)

    # 2. Fire 消防车
    if req.fire:
        firetruck, fire_detail = _predict_fire(req.fire)
        details.append(fire_detail)

    # 3. Tow 拖车
    towtruck, heavy_towtruck, tow_detail = _predict_tow(
        req.car_flip, req.vehicle_num, req.affected_lanes, req.lane_status
    )
    details.append(tow_detail)

    return DispatchResponse(
        ambulance=ambulance,
        firetruck=firetruck,
        towtruck=towtruck,
        heavy_towtruck=heavy_towtruck,
        details=details,
    )


# ============================================================
# 直接运行
# ============================================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8004)
