-- ============================================================
-- 交通事故风险智能识别与调度系统 - 数据库初始化脚本
-- 基于全部 12 个 JPA Entity 自动生成，兼容 MySQL 5.7+ / H2
-- 生成时间: 2026-07-09
-- ============================================================

CREATE DATABASE IF NOT EXISTS traffic_risk_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE traffic_risk_db;


-- ============================================================
-- 1. 系统用户表 (app_users)
-- JPA: UserAccount extends AuditableEntity
-- 角色: FIELD_OFFICER / COMMAND_CENTER / RESCUE_WORKER / ADMIN
-- ============================================================
CREATE TABLE IF NOT EXISTS app_users (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(64)  NOT NULL COMMENT '姓名',
    username         VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录用户名',
    phone            VARCHAR(32)  NULL     COMMENT '手机号',
    email            VARCHAR(128) NULL     COMMENT '邮箱',
    role             VARCHAR(32)  NOT NULL COMMENT '角色: FIELD_OFFICER/COMMAND_CENTER/RESCUE_WORKER/ADMIN',
    status           VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    rescue_center_id BIGINT       NULL     COMMENT '所属清障/救援中心ID',
    password_hash    VARCHAR(255) NOT NULL COMMENT '密码哈希 (sha256:xxx)',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_rescue_center (rescue_center_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';


-- ============================================================
-- 2. 事故事件表 (incidents) ★ 核心
-- JPA: Incident extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS incidents (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_no                     VARCHAR(40)  NOT NULL UNIQUE COMMENT '事故编号 (ACC20260707...)',

    -- 事故基本信息
    location_name                   VARCHAR(160) NOT NULL COMMENT '事故地点名称',
    address                         VARCHAR(255) NULL     COMMENT '详细地址',
    road_name                       VARCHAR(80)  NULL     COMMENT '路段名',
    initial_accident_type           VARCHAR(80)  NULL     COMMENT '初始事故类型',
    confirmed_accident_type         VARCHAR(80)  NULL     COMMENT '确认事故类型',
    description                     VARCHAR(1000) NOT NULL COMMENT '事故描述',

    -- 坐标信息 (原始 + 百度转换)
    longitude                       DOUBLE       NULL     COMMENT '原始经度',
    latitude                        DOUBLE       NULL     COMMENT '原始纬度',
    coordinate_type                 VARCHAR(16)  NULL     DEFAULT 'WGS84' COMMENT '坐标系: WGS84/GCJ02/BD09',
    baidu_longitude                 DOUBLE       NULL     COMMENT '百度BD09经度',
    baidu_latitude                  DOUBLE       NULL     COMMENT '百度BD09纬度',
    map_formatted_address           VARCHAR(255) NULL     COMMENT '百度地图标准地址',
    map_semantic_description        VARCHAR(500) NULL     COMMENT '百度地图语义描述',

    -- 现场结构化信息
    occupied_lanes                  INT          NULL     COMMENT '占道车道数',
    traffic_flow                    INT          NULL     COMMENT '车流量评估值',
    people_flow                     INT          NULL     COMMENT '行人流量评估值',
    people_involved                 INT          NULL     COMMENT '涉及人数',
    injured_count                   INT          NULL     COMMENT '受伤人数',
    injury_estimate                 VARCHAR(500) NULL     COMMENT '伤情预估描述',
    weather                         VARCHAR(40)  NULL     COMMENT '天气状况',
    road_level                      VARCHAR(40)  NULL     COMMENT '道路等级',
    road_status                     VARCHAR(80)  NULL     COMMENT '路面状态',
    casualty_detected               TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'AI是否检测到伤亡',

    -- 状态与风险
    status                          VARCHAR(32)  NOT NULL DEFAULT 'REPORTED' COMMENT '事故状态',
    risk_level                      VARCHAR(32)  NULL     COMMENT '风险等级: LOW/MEDIUM/HIGH/CRITICAL',

    -- AI预测摘要
    predicted_congestion_minutes    INT          NULL     COMMENT '预计拥堵时长(分钟)',
    predicted_recovery_minutes      INT          NULL     COMMENT '预计恢复时长(分钟)',
    confidence                      DOUBLE       NULL     COMMENT '预测置信度 0.0~1.0',
    suggestion                      VARCHAR(1000) NULL     COMMENT '处置建议',
    explanation                     VARCHAR(1500) NULL     COMMENT 'AI分析说明',

    -- 支援决策
    support_required                TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否需要支援',
    support_reason                  VARCHAR(500) NULL     COMMENT '支援判断依据',
    support_decision_manual         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否人工修改',
    support_decision_by_user_id     BIGINT       NULL     COMMENT '修改支援判断用户ID',
    support_decision_at             DATETIME     NULL     COMMENT '修改支援判断时间',

    -- 市民即时提示
    citizen_immediate_advice        VARCHAR(1000) NULL     COMMENT '面向市民的安全提示',
    estimated_police_arrival_minutes INT         NULL     COMMENT '预计交警到达(分钟)',
    police_arrival_text             VARCHAR(160) NULL     COMMENT '交警到达描述',

    -- 上报人
    report_user_id                  BIGINT       NULL     COMMENT '上报用户ID',

    created_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_no (incident_no),
    INDEX idx_status (status),
    INDEX idx_risk_level (risk_level),
    INDEX idx_report_user (report_user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交通事故记录 (核心表)';


-- ============================================================
-- 3. 事故附件表 (incident_attachments)
-- JPA: IncidentAttachment extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS incident_attachments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id         BIGINT       NOT NULL COMMENT '关联事故ID',
    file_name           VARCHAR(255) NOT NULL COMMENT '存储文件名',
    original_filename   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type        VARCHAR(80)  NULL     COMMENT 'MIME类型',
    attachment_type     VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT 'PHOTO/VIDEO/OTHER',
    file_path           VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    file_size           BIGINT       NULL     COMMENT '文件大小(字节)',
    uploaded_by         BIGINT       NULL     COMMENT '上传用户ID',
    recognition_status  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/NOT_REQUIRED',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_id (incident_id),
    INDEX idx_recognition (recognition_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事故现场附件';


-- ============================================================
-- 4. 调度任务表 (dispatch_tasks) ★ 核心
-- JPA: DispatchTask extends AuditableEntity
-- 包含完整的车辆调度与轨迹追踪字段
-- ============================================================
CREATE TABLE IF NOT EXISTS dispatch_tasks (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no                         VARCHAR(40)  NOT NULL UNIQUE COMMENT '任务编号',
    incident_id                     BIGINT       NOT NULL COMMENT '关联事故ID',
    task_type                       VARCHAR(32)  NOT NULL COMMENT '任务类型: RESCUE/AMBULANCE/POLICE/ENGINEERING',
    receiver_user_id                BIGINT       NULL     COMMENT '接收人用户ID',
    assigned_by_user_id             BIGINT       NULL     COMMENT '指派人用户ID',
    rescue_center_id                BIGINT       NULL     COMMENT '清障中心ID',

    -- 车辆调度
    vehicle_required                TINYINT(1)   NULL     COMMENT '是否需要车辆',
    vehicle_type                    VARCHAR(80)  NULL     COMMENT '车辆类型',
    emergency_vehicle_id            BIGINT       NULL     COMMENT '调度车辆ID (FK → emergency_vehicles)',
    emergency_vehicle_no            VARCHAR(40)  NULL     COMMENT '车辆编号 (冗余)',
    emergency_vehicle_name          VARCHAR(80)  NULL     COMMENT '车辆名称 (冗余)',

    -- 位置信息
    location_name                   VARCHAR(160) NULL     COMMENT '事故地点名称',
    risk_level                      VARCHAR(32)  NULL     COMMENT '风险等级',

    -- 车辆起止点坐标 (原始 WGS84)
    vehicle_start_longitude         DOUBLE       NULL     COMMENT '车辆起始经度',
    vehicle_start_latitude          DOUBLE       NULL     COMMENT '车辆起始纬度',
    vehicle_start_baidu_longitude   DOUBLE       NULL     COMMENT '车辆起始百度经度',
    vehicle_start_baidu_latitude    DOUBLE       NULL     COMMENT '车辆起始百度纬度',

    -- 事故目标点坐标
    incident_target_longitude       DOUBLE       NULL     COMMENT '事故目标经度',
    incident_target_latitude        DOUBLE       NULL     COMMENT '事故目标纬度',
    incident_target_baidu_longitude DOUBLE       NULL     COMMENT '事故目标百度经度',
    incident_target_baidu_latitude  DOUBLE       NULL     COMMENT '事故目标百度纬度',

    -- 轨迹计算
    dispatch_distance_km            DOUBLE       NULL     COMMENT '调度距离(公里)',
    dispatch_speed_kmh              DOUBLE       NULL     COMMENT '调度速度(km/h)',
    estimated_arrival_minutes       INT          NULL     COMMENT '预计到达时间(分钟)',

    -- 处置信息
    advice                          VARCHAR(1000) NULL     COMMENT '处置建议',
    feedback                        VARCHAR(1000) NULL     COMMENT '处置反馈',

    -- 状态与时间
    status                          VARCHAR(32)  NOT NULL DEFAULT 'DISPATCHED' COMMENT '调度状态',
    departed_at                     DATETIME     NULL     COMMENT '出发时间',
    arrived_at                      DATETIME     NULL     COMMENT '到达时间',
    completed_at                    DATETIME     NULL     COMMENT '完成时间',

    created_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident (incident_id),
    INDEX idx_receiver (receiver_user_id),
    INDEX idx_status (status),
    INDEX idx_vehicle (emergency_vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务';


-- ============================================================
-- 5. 应急车辆表 (emergency_vehicles)
-- JPA: EmergencyVehicle extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS emergency_vehicles (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_no          VARCHAR(40)  NOT NULL UNIQUE COMMENT '车辆编号',
    vehicle_name        VARCHAR(80)  NOT NULL COMMENT '车辆名称',
    vehicle_type        VARCHAR(32)  NOT NULL COMMENT '车辆类型: AMBULANCE/CLEARANCE_TRUCK/TOW_TRUCK/FIRE_TRUCK',
    status              VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/DISPATCHED/MAINTENANCE',

    -- 当前位置坐标
    longitude           DOUBLE       NULL     COMMENT '当前经度 (WGS84)',
    latitude            DOUBLE       NULL     COMMENT '当前纬度 (WGS84)',
    coordinate_type     VARCHAR(16)  NULL     DEFAULT 'WGS84' COMMENT '坐标系类型',
    baidu_longitude     DOUBLE       NULL     COMMENT '百度BD09经度',
    baidu_latitude      DOUBLE       NULL     COMMENT '百度BD09纬度',
    speed_kmh           DOUBLE       NULL     COMMENT '当前时速(km/h)',
    current_address     VARCHAR(255) NULL     COMMENT '当前地址描述',

    -- 任务绑定
    current_task_id     BIGINT       NULL     COMMENT '当前绑定调度任务ID',
    remark              VARCHAR(500) NULL     COMMENT '备注',

    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vehicle_no (vehicle_no),
    INDEX idx_status (status),
    INDEX idx_vehicle_type (vehicle_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急车辆';


-- ============================================================
-- 6. 清障救援中心表 (rescue_centers) ★ 指挥中心
-- JPA: RescueCenter extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS rescue_centers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL COMMENT '中心名称',
    center_type     VARCHAR(32)  NOT NULL COMMENT '类型: CLEARANCE/RESCUE/MAINTENANCE',
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
-- 7. 指挥调度决策表 (dispatch_decisions) ★ 指挥中心核心
-- JPA: DispatchDecision extends AuditableEntity
--
-- 五维外键关联:
--   incident_id      → incidents.id         (事故现场事件主键)
--   command_user_id  → app_users.id         (指挥人员主键)
--   rescue_user_id   → app_users.id         (清障人员主键)
--   rescue_center_id → rescue_centers.id    (清障中心主键)
--   dispatch_task_id → dispatch_tasks.id    (关联调度任务)
-- ============================================================
CREATE TABLE IF NOT EXISTS dispatch_decisions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id         BIGINT       NOT NULL COMMENT '事故现场事件主键',
    command_user_id     BIGINT       NOT NULL COMMENT '指挥人员主键',
    rescue_user_id      BIGINT       NULL     COMMENT '清障人员主键',
    rescue_center_id    BIGINT       NULL     COMMENT '清障中心主键',
    dispatch_task_id    BIGINT       NULL     COMMENT '关联调度任务ID',

    -- AI Agent 内容 (核心)
    agent_content       TEXT         NULL     COMMENT 'AI Agent 指挥调度分析建议 (LONGFORM TEXT)',

    -- 人工决策
    decision_summary    VARCHAR(1000) NULL     COMMENT '人工决策摘要',
    decision_type       VARCHAR(16)  NOT NULL DEFAULT 'HYBRID' COMMENT 'AUTO/MANUAL/HYBRID',
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ISSUED/EXECUTED/CLOSED',

    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident (incident_id),
    INDEX idx_command_user (command_user_id),
    INDEX idx_rescue_user (rescue_user_id),
    INDEX idx_rescue_center (rescue_center_id),
    INDEX idx_dispatch_task (dispatch_task_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指挥调度决策 (含AI Agent分析内容)';


-- ============================================================
-- 8. AI 预测结果表 (prediction_results)
-- JPA: PredictionResult extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS prediction_results (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id                 BIGINT       NOT NULL COMMENT '关联事故ID',
    accident_type               VARCHAR(80)  NOT NULL COMMENT '识别事故类型',
    risk_level                  VARCHAR(32)  NOT NULL COMMENT '风险等级',
    risk_score                  DOUBLE       NULL     COMMENT '风险评分',
    congestion_duration_minutes INT          NULL     COMMENT '预计拥堵时长(分钟)',
    recovery_duration_minutes   INT          NULL     COMMENT '预计恢复时长(分钟)',
    confidence                  DOUBLE       NULL     COMMENT '可信度 0.0~1.0',
    model_version               VARCHAR(40)  NULL     COMMENT '模型版本',
    suggestions                 VARCHAR(1000) NULL     COMMENT '处置建议',
    explanation                 VARCHAR(1500) NULL     COMMENT 'AI分析说明',
    risk_factors                VARCHAR(1000) NULL     COMMENT '风险因子',
    image_evidence              VARCHAR(1000) NULL     COMMENT '图片证据描述',
    evidence_summary            VARCHAR(1000) NULL     COMMENT '证据摘要',
    data_module_trace_id        VARCHAR(80)  NULL     COMMENT '数据模块追踪ID',
    raw_result                  VARCHAR(3000) NULL     COMMENT '原始预测结果(JSON)',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_id (incident_id),
    INDEX idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI预测分析结果';


-- ============================================================
-- 9. 操作日志表 (operation_logs)
-- JPA: OperationLog extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_logs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_user_id BIGINT       NULL     COMMENT '操作用户ID',
    operation_type   VARCHAR(80)  NOT NULL COMMENT '操作类型',
    object_type      VARCHAR(80)  NOT NULL COMMENT '操作对象类型',
    object_id        VARCHAR(80)  NULL     COMMENT '操作对象ID',
    ip_address       VARCHAR(80)  NULL     COMMENT 'IP地址',
    detail           VARCHAR(1000) NULL     COMMENT '操作详情',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_operator (operator_user_id),
    INDEX idx_object_type (object_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志';


-- ============================================================
-- 10. 通知记录表 (notification_records)
-- JPA: NotificationRecord extends AuditableEntity
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_user_id BIGINT       NULL     COMMENT '接收用户ID',
    channel          VARCHAR(32)  NOT NULL COMMENT '通知渠道: EMAIL/SMS/PUSH',
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
-- 11. 系统数据字典表 (system_data)
-- JPA: SystemData extends AuditableEntity
-- UNIQUE(category, code) 约束
-- 用途: 道路等级、风险规则、事故类型字典等结构化配置
-- ============================================================
CREATE TABLE IF NOT EXISTS system_data (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category        VARCHAR(32)  NOT NULL COMMENT '分类: ROAD/ACCIDENT_TYPE/RISK_RULE',
    code            VARCHAR(80)  NOT NULL COMMENT '编码',
    name            VARCHAR(160) NOT NULL COMMENT '名称',
    config_value    VARCHAR(2000) NULL     COMMENT '配置值 (JSON/文本)',
    description     VARCHAR(1000) NULL     COMMENT '描述',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_system_data_category_code (category, code),
    INDEX idx_category (category),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统数据字典';


-- ============================================================
-- 12. 物品表 (items)
-- JPA: Item (standalone entity, not extending AuditableEntity)
-- ============================================================
CREATE TABLE IF NOT EXISTS items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL COMMENT '物品名称',
    description     VARCHAR(500) NULL     COMMENT '描述',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统物品';

