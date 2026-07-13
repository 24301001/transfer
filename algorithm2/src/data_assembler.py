"""
统一数据
"""

from typing import Optional, Dict, Any, List
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)


FRONTEND_FIELD_MAPPING: Dict[str, str] = {
    # --- 人员伤害风险专家 ---
    "车道类型": "Lanes_or_Medians",
    "车道_隔离带": "Lanes_or_Medians",
    "路口类型": "Types_of_Junction",
    "路面类型": "Road_surface_type",
    "路面状况": "Road_surface_type",
    "光照条件": "Light_conditions",
    "光线": "Light_conditions",
    "天气状况": "Weather_conditions",
    "天气": "Weather_conditions",
    "碰撞类型": "Type_of_collision",
    "事故类型": "Type_of_collision",
    "车辆运动": "Vehicle_movement",
    "车辆行驶状态": "Vehicle_movement",
    "行人运动": "Pedestrian_movement",
    "行人状态": "Pedestrian_movement",

    # --- 交通影响风险专家 ---
    "距离": "Distance(mi)",
    "距离(英里)": "Distance(mi)",
    "温度": "Temperature(F)",
    "温度(华氏)": "Temperature(F)",
    "湿度": "Humidity(%)",
    "气压": "Pressure(in)",
    "气压(英寸汞柱)": "Pressure(in)",
    "能见度": "Visibility(mi)",
    "能见度(英里)": "Visibility(mi)",
    "风速": "Wind_Speed(mph)",
    "风速(英里时)": "Wind_Speed(mph)",
    "降水量": "Precipitation(in)",
    "降水量(英寸)": "Precipitation(in)",
    "天气条件": "Weather_Condition",
    "是否有减速带": "Bump",
    "是否有路口": "Junction",
    "是否有铁路": "Railway",
    "是否有环岛": "Roundabout",
    "是否有车站": "Station",
    "是否有停止标志": "Stop",
    "是否有交通信号灯": "Traffic_Signal",
    "是否有让行标志": "Give_Way",
    "是否有出口": "No_Exit",
    "是否有交通稳静化": "Traffic_Calming",
    "是否有斑马线": "Crossing",
    "日出日落": "Sunrise_Sunset",
    "民用曙光": "Civil_Twilight",
    "事故小时": "accident_hour",
    "星期几": "weekday",
    "是否高峰": "is_peak_hour",
    "是否夜间": "is_night",
}

# 天气 API 字段映射
WEATHER_API_FIELD_MAPPING: Dict[str, str] = {
    "temp_f": "Temperature(F)",
    "temp_c": "Temperature(C)",        # 需要转换
    "humidity": "Humidity(%)",
    "pressure_in": "Pressure(in)",
    "pressure_mb": "Pressure(mb)",     # 需要转换
    "visibility_mi": "Visibility(mi)",
    "visibility_km": "Visibility(km)", # 需要转换
    "wind_mph": "Wind_Speed(mph)",
    "wind_kph": "Wind_Speed(kmh)",     # 需要转换
    "precip_in": "Precipitation(in)",
    "precip_mm": "Precipitation(mm)",  # 需要转换
    "condition_text": "Weather_Condition",
    "weather": "Weather_Condition",
    "is_day": "Sunrise_Sunset",        # 可推导
}

# 定位/GPS API 字段映射
LOCATION_API_FIELD_MAPPING: Dict[str, str] = {
    "latitude": "latitude",
    "longitude": "longitude",
    "distance_mi": "Distance(mi)",
    "distance_km": "Distance(km)",     # 需要转换
    "road_type": "Road_surface_type",
    "junction_type": "Types_of_Junction",
    "speed_limit": "speed_limit",
}


# 车道/隔离带 规范化
LANES_OPTIONS = {
    "双向": "Two-way",
    "双向车道": "Two-way",
    "Two-way": "Two-way",
    "单向": "One-way",
    "单向车道": "One-way",
    "One-way": "One-way",
    "有隔离带": "Divided",
    "已隔离": "Divided",
    "Divided": "Divided",
    "无隔离带": "Undivided",
    "未隔离": "Undivided",
    "Undivided": "Undivided",
    "未知": "Unknown",
}

# 路口类型 规范化
JUNCTION_TYPE_OPTIONS = {
    "十字路口": "Crossing",
    "交叉口": "Crossing",
    "Crossing": "Crossing",
    "T型路口": "T-Junction",
    "丁字路口": "T-Junction",
    "T-Junction": "T-Junction",
    "Y型路口": "Y-Junction",
    "Y-Junction": "Y-Junction",
    "环岛": "Roundabout",
    "Roundabout": "Roundabout",
    "无路口": "No junction",
    "非路口": "No junction",
    "No junction": "No junction",
    "铁路道口": "Railway crossing",
    "Railway crossing": "Railway crossing",
    "未知": "Unknown",
}

# 路面类型 规范化
ROAD_SURFACE_OPTIONS = {
    "沥青": "Asphalt",
    "柏油": "Asphalt",
    "Asphalt": "Asphalt",
    "水泥": "Concrete",
    "混凝土": "Concrete",
    "Concrete": "Concrete",
    "沙石": "Gravel",
    "碎石": "Gravel",
    "Gravel": "Gravel",
    "土路": "Earth",
    "泥路": "Earth",
    "Earth": "Earth",
    "湿滑": "Wet",
    "潮湿": "Wet",
    "Wet": "Wet",
    "积雪": "Snow",
    "结冰": "Ice",
    "Snow": "Snow",
    "Ice": "Ice",
    "未知": "Unknown",
}

# 光照条件 规范化
LIGHT_OPTIONS = {
    "白天": "Daylight",
    "日光": "Daylight",
    "Daylight": "Daylight",
    "黄昏": "Twilight",
    "黎明": "Twilight",
    "Twilight": "Twilight",
    "夜间有路灯": "Night with street lights",
    "有路灯": "Night with street lights",
    "Night with street lights": "Night with street lights",
    "夜间无路灯": "Night without street lights",
    "无路灯": "Night without street lights",
    "Night without street lights": "Night without street lights",
    "隧道内": "Tunnel",
    "Tunnel": "Tunnel",
    "未知": "Unknown",
}

# 天气条件 规范化
WEATHER_OPTIONS = {
    "晴": "Clear",
    "晴天": "Clear",
    "Clear": "Clear",
    "多云": "Cloudy",
    "阴天": "Overcast",
    "阴": "Overcast",
    "Cloudy": "Cloudy",
    "Overcast": "Overcast",
    "雨": "Rain",
    "下雨": "Rain",
    "小雨": "Rain",
    "中雨": "Rain",
    "大雨": "Heavy Rain",
    "暴雨": "Heavy Rain",
    "Rain": "Rain",
    "Heavy Rain": "Heavy Rain",
    "雪": "Snow",
    "下雪": "Snow",
    "Snow": "Snow",
    "雾": "Fog",
    "有雾": "Fog",
    "Fog": "Fog",
    "大风": "Windy",
    "Windy": "Windy",
    "正常": "Normal",
    "Normal": "Normal",
    "未知": "Unknown",
}

# 碰撞类型 规范化
COLLISION_TYPE_OPTIONS = {
    "追尾": "Rear-end",
    "Rear-end": "Rear-end",
    "正面碰撞": "Head-on",
    "对撞": "Head-on",
    "Head-on": "Head-on",
    "侧面碰撞": "Side-impact",
    "侧撞": "Side-impact",
    "Side-impact": "Side-impact",
    "刮擦": "Sideswipe",
    "擦碰": "Sideswipe",
    "Sideswipe": "Sideswipe",
    "撞行人": "Pedestrian",
    "行人事故": "Pedestrian",
    "Pedestrian": "Pedestrian",
    "撞固定物": "Fixed object",
    "Fixed object": "Fixed object",
    "翻车": "Rollover",
    "侧翻": "Rollover",
    "Rollover": "Rollover",
    "多车连环": "Multi-vehicle",
    "Multi-vehicle": "Multi-vehicle",
    "未知": "Unknown",
}

# 车辆运动 规范化
VEHICLE_MOVEMENT_OPTIONS = {
    "直行": "Going straight",
    "直行中": "Going straight",
    "Going straight": "Going straight",
    "左转": "Turning left",
    "右转": "Turning right",
    "Turning left": "Turning left",
    "Turning right": "Turning right",
    "掉头": "U-turn",
    "U-turn": "U-turn",
    "变道": "Changing lane",
    "Changing lane": "Changing lane",
    "超车": "Overtaking",
    "Overtaking": "Overtaking",
    "停车": "Stopped",
    "已停": "Stopped",
    "Stopped": "Stopped",
    "倒车": "Reversing",
    "Reversing": "Reversing",
    "减速": "Slowing down",
    "Slowing down": "Slowing down",
    "未知": "Unknown",
}

# 行人运动 规范化
PEDESTRIAN_MOVEMENT_OPTIONS = {
    "横穿马路": "Crossing road",
    "过马路": "Crossing road",
    "Crossing road": "Crossing road",
    "沿路边行走": "Walking along road",
    "Walking along road": "Walking along road",
    "在人行横道": "On crosswalk",
    "斑马线": "On crosswalk",
    "On crosswalk": "On crosswalk",
    "在人行道上": "On sidewalk",
    "人行道": "On sidewalk",
    "On sidewalk": "On sidewalk",
    "站立": "Standing",
    "Standing": "Standing",
    "跑步": "Running",
    "Running": "Running",
    "无行人": "Not a pedestrian",
    "Not a pedestrian": "Not a pedestrian",
    "未知": "Unknown",
}

# 日出日落 规范化
SUNRISE_SUNSET_OPTIONS = {
    "白天": "Day",
    "日间": "Day",
    "Day": "Day",
    "夜间": "Night",
    "晚上": "Night",
    "Night": "Night",
    "未知": "Unknown",
}

# 民事曙光 规范化
CIVIL_TWILIGHT_OPTIONS = {
    "白天": "Day",
    "日间": "Day",
    "Day": "Day",
    "夜间": "Night",
    "晚上": "Night",
    "Night": "Night",
    "未知": "Unknown",
}

# 布尔字段 规范化（Bump / Crossing / Give_Way / Junction 等）
BOOLEAN_OPTIONS: Dict[str, str] = {
    "是": "True",
    "yes": "True",
    "true": "True",
    "True": "True",
    "1": "True",
    "否": "False",
    "no": "False",
    "false": "False",
    "False": "False",
    "0": "False",
}


@dataclass
class AssembledData:
    """组装完成的数据结构"""
    # 伤害专家需要的 8 个字段
    injury_data: Dict[str, Any] = field(default_factory=dict)
    # 交通专家需要的 25 个字段
    traffic_data: Dict[str, Any] = field(default_factory=dict)
    # 合并后的完整结构化数据（给两个专家共用）
    combined_data: Dict[str, Any] = field(default_factory=dict)
    # 文字描述
    description: str = ""
    # 数据来源追踪
    sources: List[str] = field(default_factory=list)
    # 警告信息
    warnings: List[str] = field(default_factory=list)


def assemble_risk_data(
    form_data: Optional[Dict[str, Any]] = None,
    weather_api: Optional[Dict[str, Any]] = None,
    location_api: Optional[Dict[str, Any]] = None,
    description: Optional[str] = None,
    raw_json: Optional[Dict[str, Any]] = None,
) -> AssembledData:
    result = AssembledData()
    merged: Dict[str, Any] = {}

    # 按优先级逐层合并
    if form_data:
        result.sources.append("form_data")
        normalized = _normalize_form_data(form_data)
        merged.update(normalized)

    if weather_api:
        result.sources.append("weather_api")
        normalized = _normalize_weather_api(weather_api)
        merged.update(normalized)

    if location_api:
        result.sources.append("location_api")
        normalized = _normalize_location_api(location_api)
        merged.update(normalized)

    if raw_json:
        result.sources.append("raw_json")
        # raw_json 中的字段名应该已经是标准格式
        for key, value in raw_json.items():
            if value is not None and value != "":
                merged[key] = value

    if description:
        result.description = description.strip()

    # 后处理：规范化 all boolean-like fields
    merged = _normalize_boolean_fields(merged)

    # 拆分给两个专家
    result.injury_data = _extract_injury_fields(merged)
    result.traffic_data = _extract_traffic_fields(merged)
    result.combined_data = merged

    logger.info(f"数据组装完成，来源: {result.sources}, "
                f"伤害字段: {len(result.injury_data)}, "
                f"交通字段: {len(result.traffic_data)}")

    return result



def _normalize_form_data(raw: Dict[str, Any]) -> Dict[str, Any]:

    result: Dict[str, Any] = {}

    for key, value in raw.items():
        if value is None or value == "":
            continue

        # 1) 字段名映射
        mapped_key = FRONTEND_FIELD_MAPPING.get(key, key)

        # 2) 值规范化
        normalized_value = _normalize_field_value(mapped_key, value)

        result[mapped_key] = normalized_value

    return result


def _normalize_weather_api(raw: Dict[str, Any]) -> Dict[str, Any]:
    """
    规范化天气 API 数据：
    """
    result: Dict[str, Any] = {}

    for api_key, value in raw.items():
        if value is None:
            continue

        mapped_key = WEATHER_API_FIELD_MAPPING.get(api_key, api_key)

        if mapped_key is None:
            continue

        # 单位转换
        converted_value = _convert_units(mapped_key, value)

        # 值规范化
        normalized_value = _normalize_field_value(mapped_key, converted_value)

        result[mapped_key] = normalized_value

    return result


def _normalize_location_api(raw: Dict[str, Any]) -> Dict[str, Any]:
    """
    规范化定位 API 数据。
    """
    result: Dict[str, Any] = {}

    for api_key, value in raw.items():
        if value is None:
            continue

        mapped_key = LOCATION_API_FIELD_MAPPING.get(api_key, api_key)

        # 单位转换（如 km → mi）
        converted_value = _convert_units(mapped_key, value)
        normalized_value = _normalize_field_value(mapped_key, converted_value)

        result[mapped_key] = normalized_value

    return result



def _normalize_field_value(field_name: str, value: Any) -> Any:
    """根据字段名，将值规范化为模型期望的格式"""
    if value is None or value == "":
        return None

    str_value = str(value).strip()

    # 伤害专家字段
    if field_name == "Lanes_or_Medians":
        return LANES_OPTIONS.get(str_value, str_value)
    elif field_name == "Types_of_Junction":
        return JUNCTION_TYPE_OPTIONS.get(str_value, str_value)
    elif field_name == "Road_surface_type":
        return ROAD_SURFACE_OPTIONS.get(str_value, str_value)
    elif field_name == "Light_conditions":
        return LIGHT_OPTIONS.get(str_value, str_value)
    elif field_name == "Weather_conditions":
        return WEATHER_OPTIONS.get(str_value, str_value)
    elif field_name == "Type_of_collision":
        return COLLISION_TYPE_OPTIONS.get(str_value, str_value)
    elif field_name == "Vehicle_movement":
        return VEHICLE_MOVEMENT_OPTIONS.get(str_value, str_value)
    elif field_name == "Pedestrian_movement":
        return PEDESTRIAN_MOVEMENT_OPTIONS.get(str_value, str_value)
    # 交通专家字段
    elif field_name == "Sunrise_Sunset":
        return SUNRISE_SUNSET_OPTIONS.get(str_value, str_value)
    elif field_name == "Civil_Twilight":
        return CIVIL_TWILIGHT_OPTIONS.get(str_value, str_value)
    elif field_name == "Weather_Condition":
        return WEATHER_OPTIONS.get(str_value, str_value)

    return value


def _normalize_boolean_fields(data: Dict[str, Any]) -> Dict[str, Any]:
    """将布尔类字段统一规范化为 True/False 字符串"""
    boolean_fields = [
        "Bump", "Crossing", "Give_Way", "Junction", "No_Exit",
        "Railway", "Roundabout", "Station", "Stop",
        "Traffic_Calming", "Traffic_Signal",
    ]
    for field in boolean_fields:
        if field in data:
            val = data[field]
            if isinstance(val, bool):
                data[field] = "True" if val else "False"
            elif isinstance(val, str):
                data[field] = BOOLEAN_OPTIONS.get(val, val)

    # is_night 特殊处理：如果 Sunrise_Sunset 是 Night 且 is_night 未设置
    if "is_night" not in data or data.get("is_night") is None:
        if data.get("Sunrise_Sunset") == "Night":
            data["is_night"] = 1
    else:
        # 规范化 is_night
        val = data["is_night"]
        if isinstance(val, bool):
            data["is_night"] = 1 if val else 0
        elif isinstance(val, str):
            normalized = BOOLEAN_OPTIONS.get(val)
            data["is_night"] = 1 if normalized == "True" else 0
        else:
            data["is_night"] = int(val)

    # is_peak_hour 规范化
    if "is_peak_hour" in data:
        val = data["is_peak_hour"]
        if isinstance(val, bool):
            data["is_peak_hour"] = 1 if val else 0
        elif isinstance(val, str):
            normalized = BOOLEAN_OPTIONS.get(val)
            data["is_peak_hour"] = 1 if normalized == "True" else 0
        else:
            data["is_peak_hour"] = int(val)

    return data


def _convert_units(field_name: str, value: Any) -> Any:
    """自动进行单位转换"""
    try:
        v = float(value)
    except (ValueError, TypeError):
        return value

    if field_name in ("Temperature(F)",) and "Temperature(C)" in str(field_name):
        pass  # 在 normalize_weather_api 中已映射

    return value



INJURY_FIELDS = [
    "Lanes_or_Medians",
    "Types_of_Junction",
    "Road_surface_type",
    "Light_conditions",
    "Weather_conditions",
    "Type_of_collision",
    "Vehicle_movement",
    "Pedestrian_movement",
]

TRAFFIC_FIELDS = [
    "Distance(mi)",
    "Temperature(F)",
    "Humidity(%)",
    "Pressure(in)",
    "Visibility(mi)",
    "Wind_Speed(mph)",
    "Precipitation(in)",
    "Weather_Condition",
    "Bump",
    "Crossing",
    "Give_Way",
    "Junction",
    "No_Exit",
    "Railway",
    "Roundabout",
    "Station",
    "Stop",
    "Traffic_Calming",
    "Traffic_Signal",
    "Sunrise_Sunset",
    "Civil_Twilight",
    "accident_hour",
    "weekday",
    "is_peak_hour",
    "is_night",
]


def _extract_injury_fields(merged: Dict[str, Any]) -> Dict[str, Any]:
    """从合并数据中提取伤害专家需要的字段"""
    return {f: merged[f] for f in INJURY_FIELDS if f in merged}


def _extract_traffic_fields(merged: Dict[str, Any]) -> Dict[str, Any]:
    """从合并数据中提取交通专家需要的字段"""
    return {f: merged[f] for f in TRAFFIC_FIELDS if f in merged}


def derive_time_features(
    timestamp: Optional[str] = None,
    hour: Optional[int] = None,
    weekday: Optional[int] = None,
) -> Dict[str, Any]:
    """
    根据时间戳推导时间相关特征。
    """
    result: Dict[str, Any] = {}

    # 从时间戳解析
    if timestamp and not hour:
        try:
            from datetime import datetime
            dt = datetime.fromisoformat(timestamp)
            hour = dt.hour
            if weekday is None:
                weekday = dt.weekday()
        except (ValueError, TypeError):
            pass

    if hour is not None:
        result["accident_hour"] = hour
        # 高峰时段: 7-9 或 17-19
        result["is_peak_hour"] = 1 if (7 <= hour <= 9 or 17 <= hour <= 19) else 0
        # 夜间: 20-5
        result["is_night"] = 1 if (hour >= 20 or hour <= 5) else 0

    if weekday is not None:
        result["weekday"] = weekday

    return result


def derive_sun_features(
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
    timestamp: Optional[str] = None,
    sunrise_sunset: Optional[str] = None,
) -> Dict[str, Any]:
    """
    根据经纬度和时间推导日出日落相关特征。
    """
    result: Dict[str, Any] = {}

    if sunrise_sunset:
        result["Sunrise_Sunset"] = SUNRISE_SUNSET_OPTIONS.get(
            sunrise_sunset, sunrise_sunset
        )
        result["Civil_Twilight"] = CIVIL_TWILIGHT_OPTIONS.get(
            sunrise_sunset, sunrise_sunset
        )
    elif timestamp:
        try:
            from datetime import datetime
            dt = datetime.fromisoformat(timestamp)
            hour = dt.hour
            # 简单推断：6-18 为白天
            is_day = "Day" if 6 <= hour <= 18 else "Night"
            result["Sunrise_Sunset"] = is_day
            result["Civil_Twilight"] = is_day
        except (ValueError, TypeError):
            pass

    return result
