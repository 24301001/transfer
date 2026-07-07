# 道路交通事故风险预估与后果预测系统

本项目是一个基于 Vue 3 + Spring Boot 的前后端分离 Web 应用，用于支持交通事故上报、事故图片上传、风险等级判断、拥堵持续时间预测、调度任务处理、清障救援协同、用户管理、操作日志和实时风险推送。

系统面向现场交警/巡查人员、交警指挥中心、道路清障/救援人员和系统管理员四类角色。当前版本以后端业务框架和接口预留为主，前端保留 Vue 3 基础工程，可继续接入事故上报页、指挥中心大屏、任务处理页和系统管理页。

## 技术栈

### 前端

- Vue 3
- Vite
- Vue Router
- Axios

### 后端

- Java 17+
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- H2 Database
- Jakarta Validation
- Server-Sent Events

## 项目结构

```text
traffic-risk-platform/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── views/
│   │   ├── services/
│   │   ├── router/
│   │   ├── App.vue
│   │   └── main.js
│   ├── public/
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── backend/
│   ├── src/main/java/com/transfer/
│   │   ├── adapter/          # 百度地图、预测模型、DeepSeek、短信、邮件等外部接口适配
│   │   ├── common/           # 异常处理、统一错误响应、密码工具
│   │   ├── config/           # 跨域配置、开发初始化数据
│   │   ├── controller/       # REST API 与 SSE 控制器
│   │   ├── dto/              # 请求与响应对象
│   │   ├── enums/            # 事故状态、风险等级、任务状态、用户角色等枚举
│   │   ├── model/            # JPA 数据模型
│   │   ├── repository/       # 数据访问层
│   │   ├── service/          # 业务逻辑层
│   │   └── TransferApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── BACKEND_API.md        # 后端接口示例与说明
│   ├── README.md
│   └── pom.xml
└── README.md
```

## 快速开始

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/api/v1/health
```

H2 控制台：

```text
http://localhost:8080/h2-console
```

H2 连接信息：

```text
JDBC URL: jdbc:h2:mem:traffic_risk_db
Username: sa
Password:
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在：

```text
http://localhost:5173
```

## 默认开发账号

系统启动时会自动初始化以下开发账号，便于接口联调和页面调试。

| 用户名 | 角色 | 密码 |
| --- | --- | --- |
| `field01` | 现场交警/巡查人员 | `123456` |
| `command01` | 交警指挥中心 | `123456` |
| `rescue01` | 清障/救援人员 | `123456` |
| `admin` | 系统管理员 | `123456` |

## 核心功能

- 事故事件上报：填写地点、道路、事故描述、占用车道数、天气等信息。
- 事故图片上传：支持上传一张或多张事故现场图片并关联事故。
- 风险评估与后果预测：输出事故类型、风险等级、预计拥堵时间、道路恢复时间和处置建议。
- 指挥中心查看：支持事故列表、详情、风险等级、预测结果和任务记录查询。
- 调度任务处理：支持创建调度任务、清障/救援人员查看任务、更新任务状态。
- 系统管理：支持用户管理、事故历史查询、操作日志查看。
- 实时推送：通过 SSE 推送事故上报、预测完成、任务创建和任务状态变化。
- 外部接口预留：已预留百度地图、预测模型服务、DeepSeek、短信和邮件适配器。

## API 接口

### 健康检查

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/health` | 后端健康检查 |
| GET | `/api/health` | 兼容前端旧健康检查地址 |

### 事故上报与预测

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/incidents` | 提交事故事件 |
| POST | `/api/v1/incidents/{incidentId}/attachments` | 上传事故现场图片 |
| POST | `/api/v1/incidents/consequence-predictions` | 获取事故类型、风险等级、拥堵时间和恢复时间预测 |
| GET | `/api/v1/incidents` | 查询事故列表，支持分页和筛选 |
| GET | `/api/v1/incidents/{incidentId}` | 查看事故详情、图片、预测结果和调度任务 |

### 调度任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/dispatch-tasks` | 创建交警、清障、救援或医疗调度任务 |
| GET | `/api/v1/dispatch-tasks` | 查询全部调度任务 |
| GET | `/api/v1/dispatch-tasks/my` | 查询当前清障/救援人员任务 |
| PUT | `/api/v1/dispatch-tasks/{taskId}/status` | 更新任务状态和处理反馈 |

### 系统管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/users` | 查询用户列表 |
| POST | `/api/v1/admin/users` | 创建用户 |
| PUT | `/api/v1/admin/users/{id}` | 修改用户 |
| DELETE | `/api/v1/admin/users/{id}` | 删除用户 |
| GET | `/api/v1/admin/operation-logs` | 查询操作日志 |

### 实时推送

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/SSE | `/api/v1/realtime/road-risk/stream` | 订阅事故风险和任务状态实时变化 |

更多请求示例见：

```text
backend/BACKEND_API.md
```

## 主要数据对象

| 对象 | 说明 |
| --- | --- |
| `UserAccount` | 用户账号、角色、联系方式和状态 |
| `Incident` | 事故事件主体信息 |
| `IncidentAttachment` | 事故现场图片附件 |
| `PredictionResult` | 事故识别、风险评估和后果预测结果 |
| `DispatchTask` | 指挥中心下发的处置任务 |
| `NotificationRecord` | 站内、短信、邮件通知记录 |
| `OperationLog` | 关键操作审计日志 |

## 外部接口预留

| 接口 | 默认实现 | 说明 |
| --- | --- | --- |
| `PredictionClient` | `DefaultPredictionClient` | 当前为规则兜底预测，后续可替换为 Python/FastAPI 模型服务 |
| `DeepSeekClient` | `DefaultDeepSeekClient` | 当前为本地文本解释，后续可接 DeepSeek API |
| `MapProvider` | `BaiduMapProvider` | 当前为百度地图占位适配，后续可接定位、逆地理编码、路线规划 |
| `NotificationProvider` | `SmsNotificationProvider` / `EmailNotificationProvider` | 当前为发送占位，后续可接短信和邮件服务商 |

## 开发说明

- 当前开发环境使用 H2 内存数据库，重启后数据会清空。
- 生产环境建议切换为 MySQL 或 PostgreSQL，并配置持久化文件存储或对象存储。
- 当前账号体系已完成用户数据结构和管理接口，正式鉴权可继续接入 Spring Security + JWT。
- 当前预测服务为兜底规则实现，便于前后端联调；真实模型接入时替换 `PredictionClient` 即可。
- 当前前端仍是基础工程，后续页面可按事故上报、指挥中心、清障任务、系统管理四类角色继续扩展。

## License

MIT
