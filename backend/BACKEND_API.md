# Road Traffic Accident Risk Backend API

Base URL: `http://localhost:8080`

## Health

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/health` | Service health and placeholder dependency status |
| GET | `/api/health` | Compatibility health endpoint |

## Incidents

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/incidents` | Create an accident incident report |
| POST | `/api/v1/incidents/{incidentId}/attachments` | Upload one incident image by `multipart/form-data`, field name `file` |
| POST | `/api/v1/incidents/consequence-predictions` | Run accident type/risk/congestion/recovery prediction |
| POST | `/api/v1/incidents/{incidentId}/prediction-results` | Receive structured prediction result from the data prediction module |
| GET | `/api/v1/incidents/{incidentId}/prediction-result/latest` | Get latest display-ready prediction result for police users |
| GET | `/api/v1/incidents` | List incidents with pagination and optional filters |
| GET | `/api/v1/incidents/{incidentId}` | Get incident detail, attachments, predictions and dispatch tasks |

Create incident example:

```json
{
  "locationName": "G4 Expressway K123",
  "address": "Northbound near exit 12",
  "longitude": 113.2644,
  "latitude": 23.1291,
  "roadName": "G4 Expressway",
  "initialAccidentType": "Rear-end collision",
  "description": "Two cars rear-ended and occupied two lanes, no visible fire.",
  "occupiedLanes": 2,
  "trafficFlow": 1800,
  "weather": "rain",
  "roadLevel": "expressway",
  "reportUserId": 1
}
```

Prediction example:

```json
{
  "incidentId": 1
}
```

Data prediction module callback example:

```json
{
  "accidentType": "Rear-end collision",
  "riskLevel": "HIGH",
  "congestionDurationMinutes": 90,
  "recoveryDurationMinutes": 150,
  "confidence": 0.87,
  "modelVersion": "traffic-risk-model-v1.2",
  "riskFactors": ["occupied_lanes=2", "traffic_flow=1800", "rain"],
  "evidenceSummary": "The image and description indicate two affected vehicles occupying two lanes.",
  "dataModuleTraceId": "pred-20260707-0001",
  "rawResult": "{\"source\":\"data-module\"}"
}
```

After receiving the callback, the backend will:

| Step | Backend action |
| --- | --- |
| 1 | Validate accident type, risk level, confidence and predicted minutes |
| 2 | Save a `PredictionResult` record |
| 3 | Update the related `Incident` as `PREDICTED` |
| 4 | Generate disposition suggestions if the data module did not provide them |
| 5 | Generate a natural-language explanation if the data module did not provide one |
| 6 | Push an `INCIDENT_PREDICTED` SSE event to police/command-center pages |

List filters:

```text
GET /api/v1/incidents?status=REPORTED&riskLevel=HIGH&keyword=G4&page=0&size=10
```

## Dispatch Tasks

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/dispatch-tasks` | Create a police, clearance, rescue or medical dispatch task |
| GET | `/api/v1/dispatch-tasks` | List all tasks with pagination |
| GET | `/api/v1/dispatch-tasks/my` | List tasks for a receiver. Use `X-User-Id` header or `receiverUserId` query |
| PUT | `/api/v1/dispatch-tasks/{taskId}/status` | Update task status and feedback |

Create task example:

```json
{
  "incidentId": 1,
  "taskType": "CLEARANCE",
  "receiverUserId": 3,
  "assignedByUserId": 2,
  "vehicleRequired": true,
  "vehicleType": "tow_truck",
  "advice": "Tow disabled vehicles first and keep one lane closed."
}
```

Update task status example:

```json
{
  "status": "ARRIVED",
  "feedback": "Arrived at the scene."
}
```

## Admin

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/admin/users` | List users |
| POST | `/api/v1/admin/users` | Create user |
| PUT | `/api/v1/admin/users/{id}` | Update user |
| DELETE | `/api/v1/admin/users/{id}` | Delete user |
| GET | `/api/v1/admin/operation-logs` | List operation logs |

Default development users are seeded on startup:

| Username | Role | Password |
| --- | --- | --- |
| `field01` | `FIELD_OFFICER` | `123456` |
| `command01` | `COMMAND_CENTER` | `123456` |
| `rescue01` | `RESCUE_WORKER` | `123456` |
| `admin` | `ADMIN` | `123456` |

## Realtime

| Method | Path | Description |
| --- | --- | --- |
| GET/SSE | `/api/v1/realtime/road-risk/stream` | Subscribe to incident and dispatch task events |

## Extension Points

| Interface | Default class | Purpose |
| --- | --- | --- |
| `PredictionClient` | `DefaultPredictionClient` | Replace with Python/FastAPI prediction model client |
| `DeepSeekClient` | `DefaultDeepSeekClient` | Replace with DeepSeek API client |
| `MapProvider` | `BaiduMapProvider` | Replace with real Baidu Map API calls |
| `NotificationProvider` | `SmsNotificationProvider`, `EmailNotificationProvider` | Replace with real SMS/email providers |

## 管理员模块补充接口

### 用户账号管理（FR-26）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/users` | 分页查询用户账号 |
| GET | `/api/v1/admin/users/{id}` | 查看用户详情 |
| POST | `/api/v1/admin/users?operatorUserId=1` | 创建用户，可设置角色和初始状态 |
| PUT | `/api/v1/admin/users/{id}?operatorUserId=1` | 修改用户资料、用户名、角色、状态和密码 |
| PATCH | `/api/v1/admin/users/{id}/status?operatorUserId=1` | 启用或禁用用户，body 示例：`{"status":"DISABLED"}` |
| POST | `/api/v1/admin/users/{id}/disable?operatorUserId=1` | 快捷禁用用户 |
| POST | `/api/v1/admin/users/{id}/enable?operatorUserId=1` | 快捷启用用户 |
| DELETE | `/api/v1/admin/users/{id}?operatorUserId=1` | 删除用户，系统会保护最后一个启用状态的管理员账号 |
| GET | `/api/v1/admin/roles` | 查看系统角色及默认权限说明 |

### 事故历史记录（FR-27）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/incidents/history` | 分页查询历史事故记录，支持 `status`、`riskLevel`、`accidentType`、`roadName`、`keyword`、`startTime`、`endTime` 筛选 |
| GET | `/api/v1/admin/incidents/{incidentId}` | 查看事故详情、附件、预测结果和调度任务 |

### 系统数据维护（FR-28）

`category` 可选值：`ROAD`、`ACCIDENT_TYPE`、`RISK_RULE`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/system-data` | 查询道路基础数据、事故类型字典、风险等级规则 |
| GET | `/api/v1/admin/system-data/{id}` | 查看系统数据详情 |
| POST | `/api/v1/admin/system-data?operatorUserId=1` | 新增系统数据 |
| PUT | `/api/v1/admin/system-data/{id}?operatorUserId=1` | 修改系统数据 |
| PATCH | `/api/v1/admin/system-data/{id}/status?operatorUserId=1` | 启用或停用系统数据，body 示例：`{"enabled":false}` |
| DELETE | `/api/v1/admin/system-data/{id}?operatorUserId=1` | 删除系统数据 |

### 操作日志和运行情况（FR-29 / FR-30）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/operation-logs` | 查询操作日志，支持 `operatorUserId`、`operationType`、`objectType`、`objectId`、`keyword`、`startTime`、`endTime` 筛选 |
| GET | `/api/v1/admin/system/status` | 查看服务健康状态、JVM 内存、用户/事故/任务/通知统计、最近 API 调用和异常日志 |
| GET | `/api/v1/admin/notification-records` | 查询通知发送记录，支持 `receiverUserId`、`channel`、`status`、`keyword`、`startTime`、`endTime` 筛选 |
| GET | `/api/v1/admin/system/api-call-logs` | 查看最近 API 调用状态 |
| GET | `/api/v1/admin/system/exception-logs` | 查看最近异常日志 |
