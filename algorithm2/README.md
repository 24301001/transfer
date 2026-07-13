# 事故风险预测专家系统

融合视觉、人员伤害风险、交通影响风险和文字描述的多模态事故风险评估 API 服务。

## 项目结构

```
al2/
├── model/                          # 模型权重文件（不要修改）
│   ├── best.pt                     # 视觉专家 YOLO 模型
│   ├── preprocessor.pkl            # 人员伤害预处理器
│   ├── xgboost_injury_model.pkl    # 人员伤害 XGBoost 模型
│   ├── preprocessor_us_traffic.pkl # 交通影响预处理器
│   └── xgboost_us_traffic_impact.pkl # 交通影响 XGBoost 模型
├── src/
│   ├── __init__.py
│   ├── config.py                   # 配置文件（映射、阈值、权重）
│   ├── schemas.py                  # Pydantic 数据模型
│   ├── main.py                     # FastAPI 主服务
│   └── models/
│       ├── __init__.py
│       ├── vision_expert.py        # 视觉专家（YOLO）
│       ├── injury_expert.py        # 人员伤害风险专家（XGBoost）
│       ├── traffic_expert.py       # 交通影响风险专家（XGBoost）
│       ├── text_expert.py          # 文字专家（规则）
│       └── fusion.py              # 专家融合策略
├── run.py                          # 启动入口
├── requirements.txt                # Python 依赖
└── README.md
```

## 快速启动

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 启动服务

```bash
python run.py
```

服务默认运行在 `http://0.0.0.0:8001`。

可通过环境变量修改：
- `HOST`: 监听地址（默认 `0.0.0.0`）
- `PORT`: 端口（默认 `8001`）

### 3. 健康检查

```bash
curl http://localhost:8001/api/health
```

## 接口说明

### POST /api/risk/predict

统一风险预测接口，支持 `multipart/form-data`。

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `image` | File | 否 | 事故现场图片（jpg/png） |
| `description` | String | 否 | 事故文字描述 |
| `structured_data` | String(JSON) | 否 | 结构化事故字段 JSON |

#### 请求示例 - cURL

```bash
# 完整请求（图片 + 结构化数据 + 描述）
curl -X POST http://localhost:8001/api/risk/predict \
  -F "image=@accident.jpg" \
  -F 'description=多车追尾，有人受伤流血，救护车已到场，高速严重拥堵' \
  -F 'structured_data={
    "Lanes_or_Medians": "Two-way",
    "Types_of_Junction": "Crossing",
    "Road_surface_type": "Asphalt",
    "Light_conditions": "Daylight",
    "Weather_conditions": "Normal",
    "Type_of_collision": "Rear-end",
    "Vehicle_movement": "Going straight",
    "Pedestrian_movement": "Not a pedestrian",
    "Distance(mi)": 0.5,
    "Temperature(F)": 72,
    "Humidity(%)": 65,
    "Pressure(in)": 30.1,
    "Visibility(mi)": 10,
    "Wind_Speed(mph)": 5,
    "Precipitation(in)": 0,
    "Weather_Condition": "Clear",
    "Bump": "False",
    "Crossing": "False",
    "Give_Way": "False",
    "Junction": "True",
    "No_Exit": "False",
    "Railway": "False",
    "Roundabout": "False",
    "Station": "False",
    "Stop": "False",
    "Traffic_Calming": "False",
    "Traffic_Signal": "True",
    "Sunrise_Sunset": "Day",
    "Civil_Twilight": "Day",
    "accident_hour": 8,
    "weekday": 2,
    "is_peak_hour": 1,
    "is_night": 0
  }'
```

```bash
# 仅结构化数据 + 描述（无图片）
curl -X POST http://localhost:8001/api/risk/predict \
  -F 'description=轻微剐蹭，无人受伤，已靠边，可通行' \
  -F 'structured_data={"Lanes_or_Medians":"Two-way","Road_surface_type":"Asphalt","Light_conditions":"Daylight","Weather_conditions":"Normal"}'
```

#### 请求示例 - Python

```python
import requests

url = "http://localhost:8001/api/risk/predict"

# 完整请求
with open("accident.jpg", "rb") as f:
    files = {"image": ("accident.jpg", f, "image/jpeg")}
    data = {
        "description": "多车追尾，有人受伤，救护车已到场",
        "structured_data": '{"Lanes_or_Medians":"Two-way","Road_surface_type":"Asphalt",...}',
    }
    response = requests.post(url, files=files, data=data)
    print(response.json())
```

#### 返回示例

```json
{
  "final_risk_level": "high",
  "final_risk_score": 3.2,
  "fusion_strategy": "weighted_sum_with_high_risk_protection",
  "experts": {
    "injury": {
      "risk_level": "medium",
      "risk_score": 2,
      "confidence": 0.82,
      "probabilities": {
        "medium": 0.82,
        "high": 0.15,
        "critical": 0.03
      }
    },
    "traffic": {
      "risk_level": "high",
      "risk_score": 3,
      "confidence": 0.76,
      "probabilities": {
        "low": 0.05,
        "medium": 0.12,
        "high": 0.76,
        "critical": 0.07
      }
    },
    "vision": {
      "risk_level": "high",
      "risk_score": 4.0,
      "confidence": 0.88
    },
    "text": {
      "text_bonus": 0.5,
      "matched_keywords": ["有人受伤", "救护车"]
    }
  }
}
```

## 专家系统架构

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  视觉专家    │  │  伤害专家    │  │  交通专家    │  │  文字专家    │
│  (YOLO)     │  │  (XGBoost)  │  │  (XGBoost)  │  │  (规则)     │
│  best.pt    │  │  injury.pkl │  │  traffic.pkl│  │  keyword    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                │
       ▼                ▼                ▼                ▼
┌──────────────────────────────────────────────────────────────────┐
│                      专家融合策略 (Fusion)                        │
│  base_score = 0.30×injury + 0.30×traffic + 0.25×vision + bonus │
│  + 高风险保护规则                                                  │
│  + 最终等级映射                                                    │
└──────────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────┐
│  final_risk  │
│  low/medium  │
│  high/critical│
└─────────────┘
```

## 融合策略详解

### 权重分配

| 专家 | 权重 |
|------|------|
| 人员伤害风险 | 0.30 |
| 交通影响风险 | 0.30 |
| 视觉专家 | 0.25 |
| 文字专家 | bonus（加成分） |

### 高风险保护规则

1. 任一专家输出 `high` 或 `critical` → `final_score ≥ 3.0`
2. 任一专家输出 `critical` → `final_score ≥ 3.5`
3. 文字包含`死亡/昏迷/夹困/起火/爆炸/危化品` → `final_score ≥ 3.6`
4. `final_score` 限制在 `[1.0, 4.0]`

### 最终等级映射

| 分数区间 | 等级 |
|----------|------|
| < 1.8 | low |
| 1.8 ~ 2.6 | medium |
| 2.6 ~ 3.4 | high |
| ≥ 3.4 | critical |

## 配置说明

所有可调参数在 `src/config.py` 中集中管理：

- `VISION_LABEL_MAPPING`: 视觉模型标签映射（适配不同 YOLO 模型类别名）
- `VISION_SCORE_MAPPING`: 视觉等级 → 分数映射
- `INJURY_SCORE_MAPPING`: 伤害等级 → 分数映射
- `TRAFFIC_SCORE_MAPPING`: 交通等级 → 分数映射
- `TEXT_KEYWORD_RULES`: 文字专家关键词和加分规则
- `FUSION_WEIGHTS`: 融合权重
- `FUSION_*_MIN`: 高风险保护阈值
- `FINAL_LEVEL_THRESHOLDS`: 最终等级映射阈值

## 注意事项

1. 模型在服务启动时加载一次，不会每次请求重复加载
2. 预处理器和 XGBoost 模型必须成对存在
3. 缺失字段会自动用 `Unknown` 或 `NaN` 填充，不会崩溃
4. 视觉专家是可选的，如果没有上传图片则跳过
5. 文字描述是可选的，没有则 `text_bonus = 0`
