-- ============================================================
-- 交通事故风险智能识别与调度系统 - 数据库初始化脚本
-- 基于前后端模型自动生成，兼容 MySQL 5.7+ / H2
-- ============================================================

CREATE DATABASE IF NOT EXISTS traffic_risk_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE traffic_risk_db;

-- ============================================================
-- 1. 系统用户表 (app_users)
-- 来源: 前端 stores/user.js + 后端 UserAccount.java
-- ============================================================
CREATE TABLE IF NOT EXISTS app_users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录用户名',
    full_name       VARCHAR(64)  NOT NULL COMMENT '姓名/昵称',
    password_hash   VARCHAR(255) NOT NULL COMMENT 'SHA-256 密码哈希',
    phone           VARCHAR(32)  NULL     COMMENT '手机号',
    email           VARCHAR(128) NULL     COMMENT '邮箱',
    role            VARCHAR(32)  NOT NULL DEFAULT 'POLICE' COMMENT 'POLICE/COMMAND/RESCUE/ADMIN',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    rescue_center_id BIGINT       NULL     COMMENT '所属清障/救援中心ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';


-- ============================================================
-- 2. 车祸事件核心表 (incidents) ★ 核心
-- 来源: 前端 views/police/ReportAccident.vue + 后端 Incident.java
-- 
-- 结构化字段:
--   是否占道        → occupied_lanes (影响车道数)
--   涉及人数        → people_involved
--   受伤人数        → injured_count
--   预计受伤情况    → injury_estimate
--   事故类型        → initial_accident_type / confirmed_accident_type
--   经纬度          → longitude / latitude
--   天气状况        → weather
--   现场照片        → incident_attachments 表 (外键关联)
-- ============================================================
CREATE TABLE IF NOT EXISTS incidents (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_no                     VARCHAR(40)  NOT NULL UNIQUE COMMENT '事故编号 (如 ACC20260707...)',

    -- ▼▼▼ 事故基本信息
    initial_accident_type           VARCHAR(80)  NULL     COMMENT '上报事故类型 (追尾/正面碰撞/侧面刮擦/侧翻/撞固定物/自燃/货物散落/其他)',
    confirmed_accident_type         VARCHAR(80)  NULL     COMMENT 'AI确认事故类型',
    description                     VARCHAR(1000) NOT NULL COMMENT '事故描述',

    -- ▼▼▼ 位置信息 (经纬度)
    location_name                   VARCHAR(160) NOT NULL COMMENT '事故地点名称',
    address                         VARCHAR(255) NULL     COMMENT '详细地址',
    road_name                       VARCHAR(80)  NULL     COMMENT '路段名 (如 G15沈海高速)',
    longitude                       DOUBLE       NULL     COMMENT '原始经度',
    latitude                        DOUBLE       NULL     COMMENT '原始纬度',
    coordinate_type                 VARCHAR(16)  NULL     DEFAULT 'WGS84' COMMENT '坐标系类型: WGS84/GCJ02/BD09',
    baidu_longitude                 DOUBLE       NULL     COMMENT '百度BD09经度 (地图转换后)',
    baidu_latitude                  DOUBLE       NULL     COMMENT '百度BD09纬度 (地图转换后)',
    map_formatted_address           VARCHAR(255) NULL     COMMENT '百度地图标准地址',
    map_semantic_description        VARCHAR(500) NULL     COMMENT '百度地图位置语义描述',

    -- ▼▼▼ 现场结构化信息
    occupied_lanes                  INT          NULL     COMMENT '是否占道 / 影响车道数 (0=未占道, ≥1=占用N条车道)',
    traffic_flow                    INT          NULL     COMMENT '车流量等级 (数值)',
    people_flow                     INT          NULL     COMMENT '行人流量 (数值)',
    people_involved                 INT          NULL     COMMENT '涉及人数 (事故相关人员总数)',
    injured_count                   INT          NULL     COMMENT '受伤人数',
    injury_estimate                 VARCHAR(500) NULL     COMMENT '预计受伤情况描述 (如"轻伤2人，重伤1人，无生命危险")',
    weather                         VARCHAR(40)  NULL     COMMENT '天气状况 (晴/多云/小雨/中雨/大雨/雪/雾)',
    road_level                      VARCHAR(40)  NULL     COMMENT '道路等级 (高速/快速路/主干道/次干道)',
    road_status                     VARCHAR(80)  NULL     COMMENT '路面状态 (干燥/湿滑/结冰/积雪)',
    casualty_detected               TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'AI是否检测到人员伤亡',

    -- ▼▼▼ 状态与风险
    status                          VARCHAR(32)  NOT NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/PREDICTION_REQUESTED/PREDICTED/DISPATCHED/PROCESSING/CLEARED/CLOSED',
    risk_level                      VARCHAR(32)  NULL     COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',

    -- ▼▼▼ AI预测结果 (冗余摘要)
    predicted_congestion_minutes    INT          NULL     COMMENT '预计拥堵时长(分钟)',
    predicted_recovery_minutes      INT          NULL     COMMENT '预计恢复时长(分钟)',
    confidence                      DOUBLE       NULL     COMMENT 'AI识别可信度 (0.0~1.0)',
    suggestion                      VARCHAR(1000) NULL     COMMENT '处置建议',
    explanation                     VARCHAR(1500) NULL     COMMENT 'AI分析说明',

    -- ▼▼▼ 支援决策
    support_required                TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否需要支援',
    support_reason                  VARCHAR(500) NULL     COMMENT '自动支援判断依据',
    support_decision_manual         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否人工修改过支援判断',
    support_decision_by_user_id     BIGINT       NULL     COMMENT '修改支援判断的用户ID',
    support_decision_at             DATETIME     NULL     COMMENT '修改支援判断的时间',

    -- ▼▼▼ 市民即时提示与到达预估
    citizen_immediate_advice        VARCHAR(1000) NULL     COMMENT '面向市民的即时安全提示',
    estimated_police_arrival_minutes INT         NULL     COMMENT '预计交警到达时间(分钟)',
    police_arrival_text             VARCHAR(160) NULL     COMMENT '预计交警到达文字描述',

    -- ▼▼▼ 上报人
    report_user_id                  BIGINT       NULL     COMMENT '上报用户ID',

    -- ▼▼▼ 审计时间
    created_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_status (status),
    INDEX idx_risk_level (risk_level),
    INDEX idx_report_user (report_user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车祸事件记录 (核心表)';


-- ============================================================
-- 3. 事故附件表 (incident_attachments) — 现场照片/视频
-- 来源: 前端 components/PhotoUploader.vue + 后端 IncidentAttachment.java
-- ============================================================
CREATE TABLE IF NOT EXISTS incident_attachments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id         BIGINT       NOT NULL COMMENT '关联事故ID',
    file_name           VARCHAR(255) NOT NULL COMMENT '存储文件名 (UUID)',
    original_filename   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type        VARCHAR(80)  NULL     COMMENT 'MIME类型',
    attachment_type     VARCHAR(32)  NOT NULL DEFAULT 'PHOTO' COMMENT 'PHOTO/VIDEO/OTHER',
    file_path           VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    file_size           BIGINT       NULL     COMMENT '文件大小(字节)',
    recognition_status  VARCHAR(32)  NOT NULL DEFAULT 'WAITING_DATA_MODULE' COMMENT 'WAITING_DATA_MODULE/PROCESSING/COMPLETED/NOT_REQUIRED',
    uploaded_by         BIGINT       NULL     COMMENT '上传用户ID',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_id (incident_id),
    INDEX idx_recognition (recognition_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事故现场照片/视频附件';


-- ============================================================
-- 4. 调度任务表 (dispatch_tasks)
-- 来源: 前端 stores/dispatch.js + 后端 DispatchTask.java
-- ============================================================
CREATE TABLE IF NOT EXISTS dispatch_tasks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no             VARCHAR(40)  NOT NULL UNIQUE COMMENT '任务编号',
    incident_id         BIGINT       NOT NULL COMMENT '关联事故ID',
    task_type           VARCHAR(32)  NOT NULL DEFAULT 'RESCUE' COMMENT 'RESCUE/AMBULANCE/POLICE/ENGINEERING',
    receiver_user_id    BIGINT       NULL     COMMENT '接收人用户ID',
    assigned_by_user_id BIGINT       NULL     COMMENT '指派人用户ID',
    rescue_center_id    BIGINT       NULL     COMMENT '清障中心ID',
    vehicle_required    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否需要车辆',
    vehicle_type        VARCHAR(80)  NULL     COMMENT '车辆类型',
    vehicle_plate       VARCHAR(32)  NULL     COMMENT '车牌号',
    location_name       VARCHAR(160) NULL     COMMENT '事故地点(冗余)',
    risk_level          VARCHAR(32)  NULL     COMMENT '风险等级(冗余)',
    advice              VARCHAR(1000) NULL     COMMENT '处置建议',
    feedback            VARCHAR(1000) NULL     COMMENT '处置反馈',
    notes               VARCHAR(500)  NULL     COMMENT '备注',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DISPATCHED' COMMENT 'DISPATCHED/DEPARTED/ARRIVED/PROCESSING/COMPLETED',
    completed_at        DATETIME     NULL     COMMENT '完成时间',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident (incident_id),
    INDEX idx_receiver (receiver_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务';


-- ============================================================
-- 5. AI预测结果表 (prediction_results)
-- 来源: 前端 ReportAccident.vue 识别结果弹窗 + 后端 PredictionResult.java
-- ============================================================
CREATE TABLE IF NOT EXISTS prediction_results (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id                 BIGINT       NOT NULL COMMENT '关联事故ID',
    accident_type               VARCHAR(80)  NOT NULL COMMENT '识别事故类型',
    risk_level                  VARCHAR(32)  NOT NULL COMMENT '风险等级',
    risk_score                  INT          NULL     COMMENT '风险评分',
    image_evidence              VARCHAR(2000) NULL     COMMENT '图片识别证据描述',
    congestion_duration_minutes INT          NULL     COMMENT '预计拥堵时长(分钟)',
    recovery_duration_minutes   INT          NULL     COMMENT '预计恢复时长(分钟)',
    confidence                  DOUBLE       NULL     COMMENT '可信度 0~1',
    model_version               VARCHAR(40)  NULL     COMMENT '模型版本',
    suggestions                 VARCHAR(1000) NULL     COMMENT '处置建议',
    explanation                 VARCHAR(1500) NULL     COMMENT 'AI分析说明',
    risk_factors                VARCHAR(1000) NULL     COMMENT '风险因子',
    evidence_summary            VARCHAR(1000) NULL     COMMENT '证据摘要',
    data_module_trace_id        VARCHAR(100)  NULL     COMMENT '数据模块追踪ID',
    raw_result                  TEXT          NULL     COMMENT '原始返回结果(JSON)',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_id (incident_id),
    INDEX idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI预测分析结果';


-- ============================================================
-- 6. 操作日志表 (operation_logs)
-- 来源: 前端 views/admin/OperationLog.vue + 后端 OperationLog.java
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_logs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_user_id BIGINT       NULL     COMMENT '操作用户ID',
    operation_type   VARCHAR(80)  NOT NULL COMMENT '操作类型',
    object_type      VARCHAR(80)  NOT NULL COMMENT '操作对象类型',
    object_id        VARCHAR(80)  NULL     COMMENT '操作对象ID',
    ip_address       VARCHAR(45)  NULL     COMMENT 'IP地址',
    detail           VARCHAR(1000) NULL     COMMENT '操作详情',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operator (operator_user_id),
    INDEX idx_object_type (object_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志';


-- ============================================================
-- 7. 通知记录表 (notification_records)
-- 来源: 后端 NotificationRecord.java (预留)
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_user_id BIGINT       NULL     COMMENT '接收用户ID',
    channel          VARCHAR(32)  NOT NULL COMMENT 'EMAIL/SMS/PUSH',
    title            VARCHAR(160) NOT NULL COMMENT '通知标题',
    content          VARCHAR(1000) NOT NULL COMMENT '通知内容',
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    failure_reason   VARCHAR(500) NULL     COMMENT '失败原因',
    sent_at          DATETIME     NULL     COMMENT '发送时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_receiver (receiver_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送记录';


-- ============================================================
-- 8. 清障救援中心表 (rescue_centers) ★ 指挥中心数据库新增
-- 来源: 后端 RescueCenter.java
-- 指挥中心通过此表管理清障救援单位，清障人员归属于某个中心
-- ============================================================
CREATE TABLE IF NOT EXISTS rescue_centers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL COMMENT '中心名称 (如"浦东清障一队")',
    center_type     VARCHAR(32)  NOT NULL COMMENT 'CLEARANCE/RESCUE/MAINTENANCE',
    address         VARCHAR(255) NULL     COMMENT '中心地址',
    longitude       DOUBLE       NULL     COMMENT '经度',
    latitude        DOUBLE       NULL     COMMENT '纬度',
    phone           VARCHAR(32)  NULL     COMMENT '联系电话',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_center_type (center_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清障救援中心';


-- ============================================================
-- 9. 指挥调度决策表 (dispatch_decisions) ★ 指挥中心数据库核心
-- 来源: 后端 DispatchDecision.java
--
-- 关联关系:
--   incident_id     → incidents.id      (事故现场事件主键)
--   command_user_id → app_users.id       (指挥人员主键)
--   rescue_user_id  → app_users.id       (清障人员主键)
--   rescue_center_id → rescue_centers.id (清障中心主键)
--   dispatch_task_id → dispatch_tasks.id (关联调度任务)
--
-- agent_content: AI Agent 生成的指挥调度分析内容
-- ============================================================
CREATE TABLE IF NOT EXISTS dispatch_decisions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- ▼▼▼ 四维外键关联
    incident_id         BIGINT       NOT NULL COMMENT '事故现场事件主键 (FK → incidents)',
    command_user_id     BIGINT       NOT NULL COMMENT '指挥人员主键 (FK → app_users)',
    rescue_user_id      BIGINT       NULL     COMMENT '清障人员主键 (FK → app_users)',
    rescue_center_id    BIGINT       NULL     COMMENT '清障中心主键 (FK → rescue_centers)',
    dispatch_task_id    BIGINT       NULL     COMMENT '关联调度任务ID (FK → dispatch_tasks)',

    -- ▼▼▼ Agent 内容
    agent_content       TEXT         NULL     COMMENT 'AI Agent生成的指挥调度分析建议内容',

    -- ▼▼▼ 人工决策
    decision_summary    VARCHAR(1000) NULL     COMMENT '人工决策摘要',
    decision_type       VARCHAR(16)  NOT NULL DEFAULT 'HYBRID' COMMENT 'AUTO/MANUAL/HYBRID',
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ISSUED/EXECUTED/CLOSED',

    -- ▼▼▼ 审计时间
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_incident (incident_id),
    INDEX idx_command_user (command_user_id),
    INDEX idx_rescue_user (rescue_user_id),
    INDEX idx_rescue_center (rescue_center_id),
    INDEX idx_dispatch_task (dispatch_task_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指挥调度决策 (含Agent分析内容)';
