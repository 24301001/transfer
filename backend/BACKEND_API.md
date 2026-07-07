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
