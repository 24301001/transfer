# Algorithm3 道路恢复推荐 API

基于 FastAPI + XGBoost/LightGBM + scikit-learn，提供交通事故道路恢复时间预测和清障策略推荐。

## 项目目录结构

```text
algorithm3/
├── recovery_api.py           # FastAPI 服务入口
├── run.py                    # 启动脚本（端口 8003）
├── requirements.txt          # API 依赖
├── README_API.md             # 本文档
├── al3.3/
│   ├── accident_recovery_dataset.csv          # 训练数据集
│   ├── best_recovery_regression_model.pkl     # 最佳回归模型
│   ├── best_recovery_classification_model.pkl # 最佳分类模型
│   ├── compare_recovery_models.py             # 多模型对比训练脚本
│   ├── train_recovery_model.py                # 单模型训练脚本
│   ├── generate_recovery_figures.py           # 可视化图表生成
│   ├── recovery_figures/                      # EDA 图表输出
│   └── recovery_model_comparison_figures/      # 模型对比图表输出
└── al3.2/
    ├── dataset_info.txt                        # 数据集说明
    └── us_accidents_recovery_dataset.csv       # 原始 US Accidents 数据集
```

## 模型说明

### 回归模型（预测恢复分钟数）

使用多个回归模型对比训练（LinearRegression, DecisionTree, RandomForest, ExtraTrees, GradientBoosting, XGBoost, LightGBM），自动选择 RMSE 最低的模型保存为 `best_recovery_regression_model.pkl`。

### 分类模型（预测是否为长时事故）

以 30 分钟为阈值，将恢复时间二分类为短时（≤30min）和长时（>30min）。多个分类模型对比（LogisticRegression, DecisionTree, RandomForest, ExtraTrees, GradientBoosting, XGBoost, LightGBM），自动选择 F1 最高的模型保存为 `best_recovery_classification_model.pkl`。

### 模型权重生成

```bash
cd al3.3
python compare_recovery_models.py
```

执行后会生成：
- `best_recovery_regression_model.pkl` — API 必需的回归模型
- `best_recovery_classification_model.pkl` — API 可选的分类模型
- `recovery_model_comparison_figures/` — 模型对比图表

## 安装依赖

```bash
pip install -r requirements.txt
```

## 启动服务

```bash
# 方式一：直接启动
python run.py

# 方式二：uvicorn 命令
uvicorn recovery_api:app --host 0.0.0.0 --port 8003

# 方式三：自定义端口
$env:PORT="8003"; python run.py   # Windows PowerShell
PORT=8003 python run.py            # Linux / macOS
```

## API 接口

### 健康检查

```text
GET /health
GET /api/health
```

返回示例：

```json
{
    "status": "UP",
    "service": "algorithm3-road-recovery",
    "regressionModel": "XGBoost",
    "classificationModel": "LightGBM"
}
```

### 道路恢复预测

```text
POST /predict
POST /api/recovery/recommend
Content-Type: application/json
```

请求示例（由后端自动构造，此处展示字段结构）：

```json
{
    "incidentId": 1,
    "incidentNo": "INC-20260714-0001",
    "description": "Two cars rear-ended, occupied two lanes, no fire",
    "accidentType": "Rear-end collision",
    "riskLevel": "HIGH",
    "riskScore": 75.0,
    "congestionDurationMinutes": 45,
    "longitude": 113.2644,
    "latitude": 23.1291,
    "occupiedLanes": 2,
    "trafficFlow": 85,
    "peopleFlow": 30,
    "roadLevel": "expressway",
    "roadStatus": "congested",
    "weather": "rain",
    "sceneLabels": "car crash,car damage",
    "initialAccidentType": "Rear-end collision",
    "attachments": [
        {
            "id": 1,
            "originalFilename": "scene_001.jpg",
            "contentType": "image/jpeg",
            "fileSize": 204800,
            "filePath": "uploads/2026/07/scene_001.jpg",
            "aiDetectedTypes": "car crash,car damage"
        }
    ]
}
```

成功响应：

```json
{
    "traceId": "alg3-a1b2c3d4e5f6",
    "status": "COMPLETED",
    "result": {
        "predictedRecoveryDurationMinutes": 65,
        "recoveryLevel": "LONG",
        "longRecoveryProbability": 0.8234,
        "classificationThresholdMinutes": 30,
        "confidence": 0.8234,
        "modelVersion": "algorithm3-recovery-reg:XGBoost+cls:LightGBM",
        "recommendation": "Algorithm3 predicts about 65 minutes for road recovery. Dispatch clearance resources early, protect key lanes first, and prepare diversion control; occupied lanes: 2.",
        "keyFactors": [
            "collision",
            "vehicle damage",
            "multiple lanes affected",
            "high traffic density",
            "high risk level"
        ],
        "metrics": [...],
        "features": {...}
    },
    "errorMessage": null
}
```

### 恢复等级说明

| 等级 | 时长范围 | 含义 |
| --- | --- | --- |
| SHORT | < 30 min | 短时恢复 |
| MEDIUM | 30–59 min | 中等恢复 |
| LONG | 60–89 min | 较长恢复 |
| CRITICAL | ≥ 90 min | 严重恢复 |

### 关键因子说明

API 会根据输入特征自动提取影响恢复时间的关键因子，包括：

- `collision` — 碰撞事故
- `vehicle damage` — 车辆损坏
- `fire or smoke` — 火灾/烟雾
- `rollover` — 侧翻
- `multiple lanes affected` — 多车道受影响
- `high traffic density` — 高交通密度
- `high risk level` — 高风险等级

## 与后端集成

后端通过 `DefaultRecoveryRecommendationClient` 调用本服务：

- 配置项：`app.recovery-module.base-url=http://127.0.0.1:8003`
- 预测接口：`POST /predict`
- 健康检查：`GET /health`
- 连接超时：5000ms
- 读取超时：60000ms

后端在收到算法2（事故类型 + 风险影响）的预测结果后，自动调用本服务增强道路恢复时间预测，并将结果合并写入 `prediction_results` 表的 recovery 字段。

## 数据库字段映射

| API 字段 | 数据库字段 | 类型 |
| --- | --- | --- |
| predictedRecoveryDurationMinutes | recovery_duration_minutes | INT |
| recoveryLevel | recovery_level | VARCHAR(32) |
| confidence | recovery_confidence | DOUBLE |
| modelVersion | recovery_model_version | VARCHAR(160) |
| recommendation | recovery_recommendation | VARCHAR(1200) |
| traceId | recovery_trace_id | VARCHAR(80) |
| keyFactors | recovery_key_factors | VARCHAR(1000) |
