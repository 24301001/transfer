"""
事故风险预测专家系统 - FastAPI 主服务
"""
import logging
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional, List, Any
from uuid import uuid4

from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from src.config import HOST, PORT, PRELOAD_MODELS
from src.schemas import PredictResponse
from src.models.vision_expert import predict_vision
from src.models.injury_expert import predict_injury
from src.models.traffic_expert import predict_traffic
from src.models.text_expert import predict_text
from src.models.fusion import fuse_experts
from src.models import (
    load_vision_model,
    load_injury_model,
    load_traffic_model,
)
from src.data_assembler import assemble_risk_data

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


class BackendAttachment(BaseModel):
    id: Optional[int] = None
    attachmentId: Optional[int] = None
    originalFilename: Optional[str] = None
    contentType: Optional[str] = None
    fileSize: Optional[int] = None
    filePath: Optional[str] = None


class BackendWeatherDetail(BaseModel):
    source: Optional[str] = None
    longitude: Optional[float] = None
    latitude: Optional[float] = None
    coordinateType: Optional[str] = None
    country: Optional[str] = None
    province: Optional[str] = None
    city: Optional[str] = None
    district: Optional[str] = None
    districtId: Optional[str] = None
    text: Optional[str] = None
    temperatureC: Optional[int] = None
    feelsLikeC: Optional[int] = None
    humidityPercent: Optional[int] = None
    windDirection: Optional[str] = None
    windClass: Optional[str] = None
    precipitation1hMm: Optional[float] = None
    cloudPercent: Optional[int] = None
    visibilityMeters: Optional[int] = None
    aqi: Optional[int] = None
    updateTime: Optional[str] = None


class BackendPredictionRequest(BaseModel):
    incidentId: Optional[int] = None
    incidentNo: Optional[str] = None
    algorithmTypes: List[str] = Field(default_factory=list)
    locationName: Optional[str] = None
    address: Optional[str] = None
    description: Optional[str] = None
    longitude: Optional[float] = None
    latitude: Optional[float] = None
    occupiedLanes: Optional[int] = None
    trafficFlow: Optional[int] = None
    peopleFlow: Optional[int] = None
    weather: Optional[str] = None
    roadLevel: Optional[str] = None
    roadName: Optional[str] = None
    attachments: List[BackendAttachment] = Field(default_factory=list)
    weatherDetail: Optional[BackendWeatherDetail] = None


def _to_backend_level(level: str) -> str:
    mapping = {
        "low": "LOW",
        "medium": "MEDIUM",
        "high": "HIGH",
        "critical": "HIGH",
    }
    return mapping.get((level or "medium").lower(), "MEDIUM")


def _score_to_percent(score: float) -> float:
    try:
        value = float(score)
    except (TypeError, ValueError):
        value = 2.0
    return round(max(0.0, min(100.0, (value - 1.0) / 3.0 * 100.0)), 2)


def _first_non_blank(*values: Optional[str]) -> Optional[str]:
    for value in values:
        if value and str(value).strip():
            return str(value).strip()
    return None


def _infer_accident_type(description: Optional[str], scene_labels: Optional[str] = None) -> str:
    text = f"{description or ''} {scene_labels or ''}"
    rules = [
        ("车辆起火", ["起火", "燃烧", "fire", "smoke"]),
        ("车辆侧翻", ["侧翻", "翻车", "car flip"]),
        ("多车碰撞", ["多车", "连环"]),
        ("追尾碰撞", ["追尾"]),
        ("行人事故", ["行人", " pedestrian"]),
        ("正面碰撞", ["正面", "对撞"]),
        ("侧面碰撞", ["侧面", "侧撞"]),
        ("车辆碰撞", ["碰撞", "撞", "car crash"]),
        ("车辆受损", ["受损", "损坏", "car damage"]),
    ]
    lowered = text.lower()
    for label, keywords in rules:
        if any(keyword.lower() in lowered for keyword in keywords):
            return label
    return "其他交通事故"


def _weather_features(detail: Optional[BackendWeatherDetail], weather: Optional[str]) -> dict:
    if not detail:
        return {"Weather_Condition": weather} if weather else {}

    features: dict[str, Any] = {}
    condition = _first_non_blank(detail.text, weather)
    if condition:
        features["Weather_Condition"] = condition
        features["Weather_conditions"] = condition
    if detail.temperatureC is not None:
        features["Temperature(F)"] = round(detail.temperatureC * 9 / 5 + 32, 2)
    if detail.humidityPercent is not None:
        features["Humidity(%)"] = detail.humidityPercent
    if detail.precipitation1hMm is not None:
        features["Precipitation(in)"] = round(detail.precipitation1hMm / 25.4, 4)
    if detail.visibilityMeters is not None:
        features["Visibility(mi)"] = round(detail.visibilityMeters / 1609.344, 3)
    return features


def _structured_from_backend(request: BackendPredictionRequest) -> dict:
    data = _weather_features(request.weatherDetail, request.weather)

    accident_type = _infer_accident_type(request.description)
    data["Type_of_collision"] = accident_type

    if request.occupiedLanes is not None:
        data["Distance(mi)"] = max(0.05, min(3.0, request.occupiedLanes * 0.15))
    if request.trafficFlow is not None:
        data["is_peak_hour"] = 1 if request.trafficFlow >= 70 else 0
    if request.roadLevel:
        data["Road_surface_type"] = request.roadLevel

    text = request.description or ""
    data["Light_conditions"] = "Unknown"
    data["Lanes_or_Medians"] = "Unknown"
    data["Types_of_Junction"] = "Crossing" if ("路口" in text or "交叉" in text) else "Unknown"
    data["Vehicle_movement"] = "Unknown"
    data["Pedestrian_movement"] = "Crossing road" if "行人" in text else "Not a pedestrian"

    return data


def _read_first_image(attachments: List[BackendAttachment]) -> tuple[Optional[bytes], List[str]]:
    evidence = []
    for attachment in attachments:
        if attachment.originalFilename:
            evidence.append(attachment.originalFilename)
        content_type = (attachment.contentType or "").lower()
        if not content_type.startswith("image/") or not attachment.filePath:
            continue
        path = Path(attachment.filePath)
        if path.exists() and path.is_file():
            return path.read_bytes(), evidence
    return None, evidence


def _risk_factors(request: BackendPredictionRequest, result: dict) -> List[str]:
    factors = []
    if request.occupiedLanes and request.occupiedLanes >= 2:
        factors.append(f"占用{request.occupiedLanes}条车道")
    if request.trafficFlow and request.trafficFlow >= 70:
        factors.append("车流量较大")
    if request.peopleFlow and request.peopleFlow >= 50:
        factors.append("人流量较大")
    weather = _first_non_blank(
        request.weatherDetail.text if request.weatherDetail else None,
        request.weather,
    )
    if weather and weather not in {"晴", "多云", "Clear", "Cloudy"}:
        factors.append(f"{weather}天气影响通行")
    matched = result.get("experts", {}).get("text", {}).get("matched_keywords", [])
    factors.extend([f"文本提及{keyword}" for keyword in matched])
    return factors


def _duration_minutes(level: str, score_percent: float) -> tuple[int, int]:
    if level == "HIGH":
        congestion = 45 + int(score_percent * 0.45)
    elif level == "MEDIUM":
        congestion = 20 + int(score_percent * 0.35)
    else:
        congestion = 10 + int(score_percent * 0.2)
    recovery = max(congestion + 10, int(congestion * 1.6))
    return congestion, recovery


def _suggestion(level: str) -> str:
    return {
        "LOW": "低风险：建议现场快速处理，设置警示标志并保持车道通行。",
        "MEDIUM": "中风险：建议安排交警到场，进行局部交通疏导并关注拥堵变化。",
        "HIGH": "高风险：建议立即调度交警、清障和必要救援力量，扩大警示范围。",
    }.get(level, "中风险：建议安排交警到场并持续关注现场变化。")


def _init_models():
    """在服务启动时加载所有模型"""
    logger.info("=" * 60)
    logger.info("开始加载专家系统模型...")
    logger.info("=" * 60)

    errors = {}

    # 加载视觉专家模型
    try:
        load_vision_model()
    except Exception as e:
        errors["vision"] = str(e)
        logger.error(f"视觉专家模型加载失败: {e}")

    # 加载人员伤害风险专家模型
    try:
        load_injury_model()
    except Exception as e:
        errors["injury"] = str(e)
        logger.error(f"人员伤害风险专家模型加载失败: {e}")

    # 加载交通影响风险专家模型
    try:
        load_traffic_model()
    except Exception as e:
        errors["traffic"] = str(e)
        logger.error(f"交通影响风险专家模型加载失败: {e}")

    if errors:
        logger.warning(f"部分模型加载失败: {errors}")
    else:
        logger.info("所有模型加载完成！")

    logger.info("=" * 60)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动时加载模型"""
    if PRELOAD_MODELS:
        _init_models()
    else:
        logger.info("跳过启动预加载模型，首次预测时按需加载。")
    yield


app = FastAPI(
    title="事故风险预测专家系统",
    description="融合视觉、伤害、交通、文字四个专家的多模态事故风险评估 API",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
async def health_check():
    """健康检查接口"""
    return {"status": "ok", "service": "事故风险预测专家系统"}


@app.get("/health")
async def backend_health_check():
    """Spring 后端使用的健康检查接口。"""
    return {"status": "UP", "service": "algorithm2-risk-impact"}


@app.post("/predict")
async def predict_for_backend(request: BackendPredictionRequest):
    """
    Spring 后端兼容接口。

    输入为 PredictionModuleRequest，输出为 PredictionModuleResponse。
    """
    trace_id = f"alg2-{uuid4().hex[:12]}"

    try:
        structured = _structured_from_backend(request)
        image_bytes, image_evidence = _read_first_image(request.attachments)

        assembled = assemble_risk_data(
            form_data=structured,
            weather_api=None,
            location_api={
                "latitude": request.latitude,
                "longitude": request.longitude,
            },
            description=request.description,
            raw_json=structured,
        )

        vision_result = None
        if image_bytes:
            try:
                vision_result = predict_vision(image_bytes)
            except Exception as e:
                logger.warning(f"后端兼容接口视觉专家推理失败，继续使用其他专家: {e}")

        injury_result = predict_injury(assembled.injury_data)
        traffic_result = predict_traffic(assembled.traffic_data)
        text_result = predict_text(assembled.description or "")
        result = fuse_experts(
            injury_result=injury_result,
            traffic_result=traffic_result,
            vision_result=vision_result,
            text_result=text_result,
        )

        backend_level = _to_backend_level(result["final_risk_level"])
        score_percent = _score_to_percent(result["final_risk_score"])
        congestion, recovery = _duration_minutes(backend_level, score_percent)
        factors = _risk_factors(request, result)
        accident_type = _infer_accident_type(request.description)
        confidences = [
            expert.get("confidence")
            for expert in result.get("experts", {}).values()
            if isinstance(expert, dict) and expert.get("confidence") is not None
        ]
        confidence = round(max(confidences), 4) if confidences else 0.75

        explanation = (
            f"算法2融合人员伤害、交通影响、视觉和文本专家，"
            f"最终判定为{backend_level}，风险得分{score_percent}。"
        )

        return {
            "traceId": trace_id,
            "status": "COMPLETED",
            "results": {
                "accidentType": {
                    "accidentType": accident_type,
                    "confidence": confidence,
                    "modelVersion": "algorithm2-expert-fusion-v1",
                    "imageEvidence": image_evidence,
                    "evidenceSummary": (
                        f"根据事故描述、结构化字段和{len(image_evidence)}个附件综合判断为{accident_type}"
                    ),
                },
                "riskImpact": {
                    "riskLevel": backend_level,
                    "riskScore": score_percent,
                    "congestionDurationMinutes": congestion,
                    "recoveryDurationMinutes": recovery,
                    "confidence": confidence,
                    "modelVersion": "algorithm2-expert-fusion-v1",
                    "riskFactors": factors,
                    "suggestion": _suggestion(backend_level),
                    "explanation": explanation,
                },
            },
            "errorMessage": None,
        }
    except Exception as e:
        logger.exception("算法2后端兼容预测失败")
        return {
            "traceId": trace_id,
            "status": "FAILED",
            "results": None,
            "errorMessage": str(e),
        }


@app.post("/api/risk/predict", response_model=PredictResponse)
async def predict_risk(
    image: Optional[UploadFile] = File(None),
    description: Optional[str] = Form(None),
    structured_data: Optional[str] = Form(None),
    form_data: Optional[str] = Form(None),
    weather_data: Optional[str] = Form(None),
    location_data: Optional[str] = Form(None),
):
    """
    统一风险预测接口。
    """
    import json

    # --- 1. 数据组装 ---

    def _parse_json(raw: Optional[str], field_name: str):
        if raw:
            try:
                return json.loads(raw)
            except json.JSONDecodeError as e:
                raise HTTPException(
                    status_code=400,
                    detail=f"{field_name} JSON 解析失败: {str(e)}",
                )
        return None

    parsed_structured = _parse_json(structured_data, "structured_data")
    parsed_form = _parse_json(form_data, "form_data")
    parsed_weather = _parse_json(weather_data, "weather_data")
    parsed_location = _parse_json(location_data, "location_data")

    # 使用数据组装器统一处理
    assembled = assemble_risk_data(
        form_data=parsed_form,
        weather_api=parsed_weather,
        location_api=parsed_location,
        description=description,
        raw_json=parsed_structured,
    )

    # --- 2. 读取图片 ---
    image_bytes = None
    if image:
        try:
            image_bytes = await image.read()
        except Exception as e:
            raise HTTPException(
                status_code=400,
                detail=f"图片读取失败: {str(e)}",
            )

    # --- 3. 调用各专家 ---

    # 视觉专家
    vision_result = None
    if image_bytes and len(image_bytes) > 0:
        try:
            vision_result = predict_vision(image_bytes)
        except Exception as e:
            logger.error(f"视觉专家推理失败: {e}")
            vision_result = None

    # 人员伤害风险专家
    try:
        injury_result = predict_injury(assembled.injury_data)
    except Exception as e:
        logger.error(f"人员伤害专家推理失败: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"人员伤害专家推理失败: {str(e)}",
        )

    # 交通影响风险专家
    try:
        traffic_result = predict_traffic(assembled.traffic_data)
    except Exception as e:
        logger.error(f"交通影响专家推理失败: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"交通影响专家推理失败: {str(e)}",
        )

    # 文字专家
    text_result = predict_text(assembled.description or "")

    # --- 融合 ---
    result = fuse_experts(
        injury_result=injury_result,
        traffic_result=traffic_result,
        vision_result=vision_result,
        text_result=text_result,
    )

    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=HOST, port=PORT)
