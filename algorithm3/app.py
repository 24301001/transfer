from __future__ import annotations

import uuid
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="Traffic Prediction Module")


class AttachmentPayload(BaseModel):
    attachment_id: int | None = None
    original_filename: str | None = None
    content_type: str | None = None
    file_size: int | None = None
    file_path: str | None = None


class PredictionRequest(BaseModel):
    incident_id: int
    incident_no: str | None = None
    algorithm_types: list[str] = Field(default_factory=list)

    description: str | None = None
    longitude: float | None = None
    latitude: float | None = None
    occupied_lanes: int | None = 0
    traffic_flow: int | None = 0
    people_flow: int | None = 0
    weather: str | None = None
    road_level: str | None = None
    road_name: str | None = None

    attachments: list[AttachmentPayload] = Field(default_factory=list)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/predict")
def predict(request: PredictionRequest) -> dict[str, Any]:
    accident_type = infer_accident_type(request.description or "")
    risk_result = infer_risk(request, accident_type)

    return {
        "trace_id": f"pred-{uuid.uuid4().hex[:12]}",
        "status": "COMPLETED",
        "results": {
            "accident_type": {
                "accident_type": accident_type,
                "confidence": 0.86,
                "model_version": "teaching-rule-v1",
                "image_evidence": [
                    item.original_filename
                    for item in request.attachments
                    if item.original_filename
                ],
                "evidence_summary": (
                    f"根据事故描述和 {len(request.attachments)} 个附件，"
                    f"初步判断为{accident_type}"
                ),
            },
            "risk_impact": risk_result,
        },
    }


def infer_accident_type(description: str) -> str:
    text = description.strip()

    if "追尾" in text:
        return "追尾碰撞"
    if "起火" in text or "燃烧" in text:
        return "车辆起火"
    if "行人" in text:
        return "行人事故"
    if "泄漏" in text:
        return "危险品泄漏"
    if "刮擦" in text or "剐蹭" in text:
        return "刮擦"
    if "连环" in text or "多车" in text:
        return "多车连环碰撞"
    if "正面" in text:
        return "正面碰撞"
    if "侧面" in text:
        return "侧面碰撞"

    return "其他"


def infer_risk(
    request: PredictionRequest,
    accident_type: str,
) -> dict[str, Any]:
    occupied_lanes = request.occupied_lanes or 0
    traffic_flow = request.traffic_flow or 0

    score = 20.0
    factors: list[str] = []

    if occupied_lanes >= 2:
        score += 30
        factors.append(f"占用{occupied_lanes}条车道")
    elif occupied_lanes == 1:
        score += 15
        factors.append("占用1条车道")

    if traffic_flow >= 80:
        score += 25
        factors.append(f"车流量较大({traffic_flow})")
    elif traffic_flow >= 50:
        score += 15
        factors.append(f"车流量中等({traffic_flow})")

    if request.weather and request.weather not in ("晴", "多云"):
        score += 10
        factors.append(f"{request.weather}天气可能影响道路通行")

    if accident_type in ("车辆起火", "危险品泄漏", "多车连环碰撞"):
        score += 25
        factors.append(f"{accident_type}处置复杂")

    score = min(score, 100.0)

    if score >= 85:
        risk_level = "CRITICAL"
    elif score >= 65:
        risk_level = "HIGH"
    elif score >= 40:
        risk_level = "MEDIUM"
    else:
        risk_level = "LOW"

    congestion_minutes = round(10 + score * 0.5)
    recovery_minutes = round(20 + score * 0.9)

    return {
        "risk_level": risk_level,
        "risk_score": score,
        "congestion_duration_minutes": congestion_minutes,
        "recovery_duration_minutes": recovery_minutes,
        "confidence": 0.82,
        "model_version":"teaching-rule-v1",
        "risk_factors": factors,
        "suggestion": build_suggestion(risk_level),
        "explanation": (
            f"事故类型为{accident_type}，风险得分为{score}，"
            f"风险等级为{risk_level}。"
        ),
    }


def build_suggestion(risk_level: str) -> str:
    suggestions = {
        "LOW": "建议现场快速处理，避免长时间占道。",
        "MEDIUM": "建议安排交警到场，并进行局部交通疏导。",
        "HIGH": "建议立即调度交警及清障车辆，并设置临时警示区域。",
        "CRITICAL": "建议启动应急响应，同时调度交警、消防、医疗和清障力量。",
    }
    return suggestions[risk_level]
