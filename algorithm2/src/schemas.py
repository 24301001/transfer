from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List


class StructuredData(BaseModel):
    """结构化事故字段"""
    # 人员伤害风险专家字段
    Lanes_or_Medians: Optional[str] = None
    Types_of_Junction: Optional[str] = None
    Road_surface_type: Optional[str] = None
    Light_conditions: Optional[str] = None
    Weather_conditions: Optional[str] = None
    Type_of_collision: Optional[str] = None
    Vehicle_movement: Optional[str] = None
    Pedestrian_movement: Optional[str] = None

    # 交通影响风险专家字段
    Distance_mi: Optional[float] = Field(default=None, alias="Distance(mi)")
    Temperature_F: Optional[float] = Field(default=None, alias="Temperature(F)")
    Humidity_percent: Optional[float] = Field(default=None, alias="Humidity(%)")
    Pressure_in: Optional[float] = Field(default=None, alias="Pressure(in)")
    Visibility_mi: Optional[float] = Field(default=None, alias="Visibility(mi)")
    Wind_Speed_mph: Optional[float] = Field(default=None, alias="Wind_Speed(mph)")
    Precipitation_in: Optional[float] = Field(default=None, alias="Precipitation(in)")
    Weather_Condition: Optional[str] = None
    Bump: Optional[str] = None
    Crossing: Optional[str] = None
    Give_Way: Optional[str] = None
    Junction: Optional[str] = None
    No_Exit: Optional[str] = None
    Railway: Optional[str] = None
    Roundabout: Optional[str] = None
    Station: Optional[str] = None
    Stop: Optional[str] = None
    Traffic_Calming: Optional[str] = None
    Traffic_Signal: Optional[str] = None
    Sunrise_Sunset: Optional[str] = None
    Civil_Twilight: Optional[str] = None
    accident_hour: Optional[int] = None
    weekday: Optional[int] = None
    is_peak_hour: Optional[int] = None
    is_night: Optional[int] = None

    class Config:
        populate_by_name = True
        extra = "allow"


class PredictRequest(BaseModel):
    """预测请求"""
    structured_data: Optional[StructuredData] = None
    description: Optional[str] = None


class ExpertResult(BaseModel):
    """单个专家输出"""
    risk_level: Optional[str] = None
    risk_score: Optional[float] = None
    confidence: Optional[float] = None
    probabilities: Optional[Dict[str, float]] = None


class VisionExpertResult(ExpertResult):
    """视觉专家输出（无 probabilities 字段覆盖）"""
    pass


class TextExpertResult(BaseModel):
    """文字专家输出"""
    text_bonus: float = 0.0
    matched_keywords: List[str] = Field(default_factory=list)


class PredictResponse(BaseModel):
    """预测响应"""
    final_risk_level: str
    final_risk_score: float
    fusion_strategy: str = "weighted_sum_with_high_risk_protection"
    experts: Dict[str, Any]
