-- ============================================================
-- seed.sql
-- 道路交通事故风险预估与后果预测平台
-- 必须在 init.sql 之后执行
-- 数据严格对应当前 DemoDataInitializer
-- 4 个演示账号默认密码均为：123456
-- ============================================================

SET NAMES utf8mb4;

USE `traffic_risk_db`;

START TRANSACTION;


-- ============================================================
-- 1. 演示用户
--
-- UserRole：
-- FIELD_OFFICER
-- COMMAND_CENTER
-- RESCUE_WORKER
-- ADMIN
--
-- UserStatus：
-- ENABLED
-- DISABLED
-- ============================================================

INSERT INTO `app_users`
(
  `id`,
  `full_name`,
  `username`,
  `phone`,
  `email`,
  `role`,
  `status`,
  `password_hash`,
  `email_verified`,
  `created_at`,
  `updated_at`
)
VALUES
(
  1,
  '张警官',
  'police1',
  '13800000001',
  'police1@example.com',
  'FIELD_OFFICER',
  'ENABLED',
  'pbkdf2:120000:BOMBEdVEEmqcH8zqIBn1Kw==:rqIzgKvf21QfZuVkDglKrcbZkp3RxwNf9nR5/JaZIXI=',
  1,
  NOW(6),
  NOW(6)
),
(
  2,
  '李指挥',
  'command1',
  '13800000002',
  'command1@example.com',
  'COMMAND_CENTER',
  'ENABLED',
  'pbkdf2:120000:iEDiBlsNfI5GVTXgiZMISA==:vpMA/0eHebiSuffTHO104Jf4lwHuLzhZBJhhREEVZkA=',
  1,
  NOW(6),
  NOW(6)
),
(
  3,
  '王队长',
  'rescue1',
  '13800000003',
  'rescue1@example.com',
  'RESCUE_WORKER',
  'ENABLED',
  'pbkdf2:120000:hIx4+pWeYel6ToW4bGgBDw==:mSqVtWyVstzFWHtk2c8i1hHGf9potQ9+Hr0EUw3y664=',
  1,
  NOW(6),
  NOW(6)
),
(
  4,
  '赵管理',
  'admin1',
  '13800000004',
  'admin1@example.com',
  'ADMIN',
  'ENABLED',
  'pbkdf2:120000:0/6bUfd2xWHHRSlka5qTrA==:ZTrCJJXnmL8ZcO6C7Oda68/d2TndkuSXJVMYoNgfc1c=',
  1,
  NOW(6),
  NOW(6)
);


-- ============================================================
-- 2. 应急车辆
--
-- VehicleType：
-- AMBULANCE
-- CLEARANCE_TRUCK
--
-- VehicleStatus：
-- AVAILABLE
-- DISPATCHED
-- EN_ROUTE
-- ARRIVED
-- OUT_OF_SERVICE
--
-- CoordinateType：
-- WGS84
-- GCJ02
-- BD09
-- ============================================================

INSERT INTO `emergency_vehicles`
(
  `id`,
  `vehicle_no`,
  `vehicle_name`,
  `vehicle_type`,
  `status`,
  `longitude`,
  `latitude`,
  `coordinate_type`,
  `baidu_longitude`,
  `baidu_latitude`,
  `speed_kmh`,
  `current_address`,
  `current_task_id`,
  `remark`,
  `created_at`,
  `updated_at`
)
VALUES
(
  1,
  'AMB-001',
  '一号救护车',
  'AMBULANCE',
  'AVAILABLE',
  104.070000,
  30.660000,
  'WGS84',
  104.070000,
  30.660000,
  48.0,
  '成都市第一人民医院附近',
  NULL,
  NULL,
  NOW(6),
  NOW(6)
),
(
  2,
  'AMB-002',
  '二号救护车',
  'AMBULANCE',
  'AVAILABLE',
  104.090000,
  30.675000,
  'WGS84',
  104.090000,
  30.675000,
  45.0,
  '成都市急救中心附近',
  NULL,
  NULL,
  NOW(6),
  NOW(6)
),
(
  3,
  'CLR-001',
  '一号清障车',
  'CLEARANCE_TRUCK',
  'AVAILABLE',
  104.060000,
  30.670000,
  'WGS84',
  104.060000,
  30.670000,
  35.0,
  '清障车停车场A区',
  NULL,
  NULL,
  NOW(6),
  NOW(6)
),
(
  4,
  'CLR-002',
  '二号清障车',
  'CLEARANCE_TRUCK',
  'AVAILABLE',
  104.100000,
  30.650000,
  'WGS84',
  104.100000,
  30.650000,
  32.0,
  '清障车停车场B区',
  NULL,
  NULL,
  NOW(6),
  NOW(6)
);


-- ============================================================
-- 3. 系统字典数据
--
-- SystemDataCategory：
-- ROAD
-- ACCIDENT_TYPE
-- RISK_RULE
--
-- 重要字段映射：
-- Java 字段 value
-- 数据库列 config_value
-- ============================================================

INSERT INTO `system_data`
(
  `id`,
  `category`,
  `code`,
  `name`,
  `config_value`,
  `description`,
  `enabled`,
  `sort_order`,
  `created_at`,
  `updated_at`
)
VALUES
(
  1,
  'ROAD',
  'EXPRESSWAY',
  '高速公路',
  '{"level":"HIGHWAY","defaultSpeedLimit":120}',
  '道路基础数据示例',
  1,
  10,
  NOW(6),
  NOW(6)
),
(
  2,
  'ROAD',
  'URBAN_MAIN',
  '城市主干路',
  '{"level":"ARTERIAL","defaultSpeedLimit":60}',
  '道路基础数据示例',
  1,
  20,
  NOW(6),
  NOW(6)
),
(
  3,
  'ACCIDENT_TYPE',
  'REAR_END',
  '追尾事故',
  '{"defaultRisk":"MEDIUM"}',
  '事故类型字典示例',
  1,
  10,
  NOW(6),
  NOW(6)
),
(
  4,
  'ACCIDENT_TYPE',
  'ROLLOVER',
  '车辆侧翻',
  '{"defaultRisk":"HIGH"}',
  '事故类型字典示例',
  1,
  20,
  NOW(6),
  NOW(6)
),
(
  5,
  'RISK_RULE',
  'HIGH_RISK_LANE_BLOCK',
  '多车道占用高风险规则',
  '{"occupiedLanesGte":2,"riskLevel":"HIGH"}',
  '风险等级规则示例',
  1,
  10,
  NOW(6),
  NOW(6)
);


<<<<<<< HEAD
-- ============================================================
-- 4. 事故事件 (6条) — 完整结构化字段
-- ============================================================
INSERT INTO incidents (
    incident_no, location_name, address, road_name, longitude, latitude, coordinate_type,
    baidu_longitude, baidu_latitude,
    initial_accident_type, scene_labels, description,
    occupied_lanes, traffic_flow, people_flow,
    people_involved, injured_count, injury_reported, injury_estimate,
    weather, road_level, road_status,
    status, risk_level,
    predicted_congestion_minutes, predicted_recovery_minutes, confidence,
    suggestion, explanation,
    casualty_detected, support_required, support_reason,
    citizen_immediate_advice,
    report_user_id
) VALUES

-- 事故1: 追尾，低风险
('ACC20260707001', 'G15 沈海高速 K1200+500 段（北向）',
 '上海市浦东新区G15沈海高速北行K1200处', 'G15 沈海高速', 121.55, 31.25, 'WGS84',
 121.560, 31.255,
 '追尾事故', 'car crash,car damage', '小型轿车与SUV追尾，占用左侧车道，1人轻伤',
 1, 3, 10,
 3, 1, 1, '轻伤1人（驾驶员擦伤），其余人员无恙',
 '晴', '高速', '干燥',
 'REPORTED', 'LOW',
 25, 35, 0.92,
 '1. 在事故后方150米处放置警示标志；2. 引导车辆从左侧车道绕行；3. 通知清障车到场拖离',
 '【系统分析】经图像识别分析，判定为追尾事故，置信度92%。平峰时段高速路段，晴好天气，影响1条车道。',
 1, 0, '当前未触发自动支援条件',
 '请保持安全距离，注意减速避让，等待交警处理',
 1),

-- 事故2: 车辆碰撞，高风险
('ACC20260707002', 'G2 京沪高速 K980+200 段（南向）',
 '上海市嘉定区G2京沪高速南行K980处', 'G2 京沪高速', 121.28, 31.32, 'WGS84',
 121.290, 31.325,
 '车辆碰撞', 'car crash,car damage', '两辆货车发生侧面碰撞，占用中间两条车道，2人重伤',
 2, 5, 5,
 4, 2, 1, '重伤2人（疑似骨折），需120急救',
 '多云', '高速', '干燥',
 'PROCESSING', 'HIGH',
 60, 90, 0.87,
 '1. 封闭事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物；4. 通知120急救',
 '【系统分析】经图像识别分析，判定为车辆碰撞，置信度87%。高峰时段，多云天气，影响2条车道。风险评估为"高"等级。',
 1, 1, '风险等级为高，建议调度交警和清障/救援资源',
 '事故路段已封闭，请提前从嘉定出口绕行G312国道',
 5),

-- 事故3: 施工占道，中风险
('ACC20260707003', '中环路 汶水路段（西向）',
 '上海市普陀区中环路汶水路段', '中环路', 121.40, 31.26, 'WGS84',
 121.410, 31.265,
 '施工占道', 'car damage', '道路施工占道，导致双向各仅剩一条车道通行',
 2, 6, 20,
 0, 0, 0, NULL,
 '小雨', '快速路', '湿滑',
 'CLEARED', 'MEDIUM',
 45, 60, 0.85,
 '1. 封闭事故区域；2. 放置警示标志和锥桶；3. 疏导后方车辆减速慢行',
 '【系统分析】施工占道导致通行能力下降，高峰时段叠加小雨天气，预计拥堵45分钟。',
 0, 0, '当前未触发自动支援条件',
 '雨天路滑，请减速慢行，注意施工区域变道',
 1),

-- 事故4: 货物散落，严重风险
('ACC20260707004', 'S20 外环高速 K85+300 段（内圈）',
 '上海市闵行区S20外环高速内圈K85处', 'S20 外环高速', 121.38, 31.12, 'WGS84',
 121.390, 31.125,
 '货物散落', 'car damage', '货车货物散落，占用全部车道，1人受轻伤，需吊车清理',
 3, 4, 8,
 2, 1, 1, '轻伤1人（货主被货物擦伤），无生命危险',
 '晴', '高速', '干燥',
 'PROCESSING', 'CRITICAL',
 90, 120, 0.94,
 '1. 封闭全部事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物；4. 安排120到场检查',
 '【系统分析】货物散落占用全部3条车道，置信度94%。严重阻断交通，预计拥堵90分钟，恢复需120分钟。',
 1, 1, '风险等级为严重，建议立即调度交警、救援、医疗和清障资源',
 'S20外环内圈K85段全部封闭，请从虹梅南路出口提前绕行',
 5),

-- 事故5: 车辆自燃，高风险
('ACC20260707005', '南北高架 北京路段（南向）',
 '上海市黄浦区南北高架北京路段', '南北高架', 121.47, 31.23, 'WGS84',
 121.480, 31.235,
 '车辆自燃', 'fire/smoke,car damage', '车辆自燃，火势已控制，占用应急车道和部分行车道',
 2, 7, 30,
 1, 0, 0, '无人员伤亡，车辆已烧毁',
 '晴', '快速路', '干燥',
 'REPORTED', 'HIGH',
 55, 80, 0.89,
 '1. 放置警示标志和锥桶；2. 疏导后方车辆减速慢行；3. 通知消防确认无复燃风险；4. 通知拖车救援',
 '【系统分析】图像识别判定为车辆自燃，置信度89%。高峰时段快速路，影响2条车道。',
 0, 1, '风险等级为高，建议调度交警和清障/救援资源',
 '南北高架南行北京路段减速慢行，注意避让救援车辆',
 1),

-- 事故6: 护栏损坏，低风险
('ACC20260707006', '延安高架 虹桥枢纽入口（东向）',
 '上海市长宁区延安高架虹桥枢纽', '延安高架', 121.35, 31.21, 'WGS84',
 121.360, 31.215,
 '追尾事故', 'car damage', '护栏损坏，车辆单方事故，占用紧急停车带',
 1, 2, 8,
 1, 0, 0, NULL,
 '大雾', '主干道', '干燥',
 'CLOSED', 'LOW',
 15, 25, 0.78,
 '1. 在事故后方150米处放置警示标志；2. 引导车辆从左侧车道绕行',
 '【系统分析】单方事故仅占用紧急停车带，大雾天气需注意能见度。风险低。',
 0, 0, '当前未触发自动支援条件',
 '大雾天气，请开启雾灯，保持车距，减速慢行',
 5);


-- ============================================================
-- 5. 事故附件 (4条)
-- ============================================================
INSERT INTO incident_attachments (
    incident_id, file_name, original_filename, content_type, attachment_type,
    file_path, file_size, uploaded_by, recognition_status,
    ai_detected_types, annotated_file_url, reviewed
) VALUES
(1, 'a1b2c3d4-001.jpg', '现场照片_追尾_1.jpg', 'image/jpeg', 'PHOTO', '/uploads/a1b2c3d4-001.jpg', 245760,  1, 'COMPLETED', 'car crash,car damage', NULL, 0),
(1, 'a1b2c3d4-002.jpg', '现场照片_追尾_2.jpg', 'image/jpeg', 'PHOTO', '/uploads/a1b2c3d4-002.jpg', 312400,  1, 'COMPLETED', 'car damage', NULL, 0),
(2, 'e5f6g7h8-001.jpg', '现场照片_碰撞.jpg',     'image/jpeg', 'PHOTO', '/uploads/e5f6g7h8-001.jpg', 189320,  5, 'COMPLETED', 'car crash,car damage', NULL, 0),
(4, 'i9j0k1l2-001.mp4', '现场视频_散落.webm',    'video/webm', 'VIDEO', '/uploads/i9j0k1l2-001.mp4', 5242880, 5, 'COMPLETED', 'car damage', NULL, 0);


-- ============================================================
-- 6. 调度任务 (5条) — 含车辆轨迹字段
-- ============================================================
INSERT INTO dispatch_task (
    task_no, incident_id, task_type,
    receiver_user_id, assigned_by_user_id, rescue_center_id,
    vehicle_required, vehicle_type,
    emergency_vehicle_id, emergency_vehicle_no, emergency_vehicle_name,
    location_name, risk_level,
    vehicle_start_longitude, vehicle_start_latitude,
    vehicle_start_baidu_longitude, vehicle_start_baidu_latitude,
    incident_target_longitude, incident_target_latitude,
    incident_target_baidu_longitude, incident_target_baidu_latitude,
    dispatch_distance_km, dispatch_speed_kmh, estimated_arrival_minutes,
    advice, status,
    departed_at, arrived_at, completed_at
) VALUES

-- 任务1: 追尾 → 浦东清障一队 (已完成)
('TASK-20260707-001', 1, 'RESCUE',
 3, 2, 1,
 1, '清障车',
 1, '沪A·B3456', '浦东一号清障车',
 'G15 沈海高速 K1200+500 段（北向）', 'LOW',
 121.55, 31.25, 121.560, 31.255,
 121.55, 31.25, 121.560, 31.255,
 3.2, 45, 8,
 '常规处置即可，注意现场安全', 'COMPLETED',
 '2026-07-07 10:05:00', '2026-07-07 10:13:00', '2026-07-07 10:35:00'),

-- 任务2: 碰撞 → 嘉定清障救援队 (处置中)
('TASK-20260707-002', 2, 'RESCUE',
 6, 2, 2,
 1, '清障车',
 2, '沪B·12345', '嘉定一号清障车',
 'G2 京沪高速 K980+200 段（南向）', 'HIGH',
 121.20, 31.30, 121.210, 31.305,
 121.28, 31.32, 121.290, 31.325,
 5.1, 50, 12,
 '需立即封锁车道，携带破拆工具', 'PROCESSING',
 '2026-07-07 10:12:00', '2026-07-07 10:24:00', NULL),

-- 任务3: 碰撞 → 救护车 (已派发)
('TASK-20260707-003', 2, 'AMBULANCE',
 6, 2, 2,
 1, '救护车',
 4, '沪B·D4567', '嘉定一号救护车',
 'G2 京沪高速 K980+200 段（南向）', 'HIGH',
 121.20, 31.30, 121.210, 31.305,
 121.28, 31.32, 121.290, 31.325,
 5.1, 60, 10,
 '现场有2人重伤，需紧急送医', 'DISPATCHED',
 NULL, NULL, NULL),

-- 任务4: 货物散落 → 浦东工程车 (已派发)
('TASK-20260707-004', 4, 'RESCUE',
 3, 2, 1,
 1, '工程车',
 3, '沪A·C7890', '浦东工程车',
 'S20 外环高速 K85+300 段（内圈）', 'CRITICAL',
 121.55, 31.25, 121.560, 31.255,
 121.38, 31.12, 121.390, 31.125,
 6.8, 40, 15,
 '需要吊车清理散落货物（约3吨）', 'DISPATCHED',
 NULL, NULL, NULL),

-- 任务5: 自燃 → 嘉定清障车 (已派发)
('TASK-20260707-005', 5, 'RESCUE',
 6, 2, 2,
 1, '清障车',
 2, '沪B·12345', '嘉定一号清障车',
 '南北高架 北京路段（南向）', 'HIGH',
 121.20, 31.30, 121.210, 31.305,
 121.47, 31.23, 121.480, 31.235,
 8.5, 35, 18,
 '注意漏油和复燃风险，需平板拖车', 'DISPATCHED',
 NULL, NULL, NULL);


-- ============================================================
-- 7. AI 预测结果 (3条)
-- ============================================================
INSERT INTO prediction_results (
    incident_id, accident_type, risk_level, risk_score,
    congestion_duration_minutes, recovery_duration_minutes, confidence,
    model_version, suggestions, explanation, risk_factors, image_evidence, evidence_summary
) VALUES
(1, '追尾事故', 'LOW',      25.0,
 25,  35,  0.92, 'v2.1.0',
 '1. 在事故后方150米处放置警示标志；2. 引导车辆绕行；3. 通知清障车到场拖离',
 '【系统分析结果】经图像识别与多源数据综合分析，该事件被判定为"追尾事故"，置信度92.0%。平峰时段高速路段，晴好天气，影响1条车道。风险评估为"低"等级。',
 '低速碰撞、单车损',
 '图像显示两车追尾，前车后保险杠变形，后车前盖轻微翘起',
 '前后保险杠变形、车身无严重损毁'),

(2, '车辆碰撞', 'HIGH',     72.0,
 60,  90,  0.87, 'v2.1.0',
 '1. 封闭事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物；4. 通知120急救',
 '【系统分析结果】经图像识别与多源数据综合分析，该事件被判定为"车辆碰撞"，置信度87.0%。高峰时段高速路段，多云天气，影响2条车道。风险评估为"高"等级。图像检测到人员受伤迹象。',
 '高速碰撞、货物散落、人员受伤',
 '图像显示两辆货车侧面碰撞，左侧货车车厢撕裂，货物部分散落',
 '货车车厢撕裂、货物散落、人员受伤迹象'),

(4, '货物散落', 'CRITICAL', 92.0,
 90, 120, 0.94, 'v2.1.0',
 '1. 封闭全部事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物；4. 安排人员到场检查伤情',
 '【系统分析结果】经图像识别与多源数据综合分析，该事件被判定为"货物散落"，置信度94.0%。货物完全阻断三条车道，风险评估为"严重"等级。',
 '全车道阻断、重型货物、潜在人员风险',
 '图像显示大量货物散落在三条车道上，疑似建材类重物',
 '全车道阻断、重型建材散落、需吊车清理');


-- ============================================================
-- 8. 指挥调度决策 (4条) ★ 指挥中心核心 — AI Agent 内容
-- ============================================================
INSERT INTO dispatch_decisions (
    incident_id, command_user_id, rescue_user_id, rescue_center_id, dispatch_task_id,
    agent_content, decision_summary, decision_type, status
) VALUES

-- 决策1: 追尾事故 → 浦东清障一队 王队长
(1, 3, 4, 1, 1,
 '【AI Agent 指挥调度分析】
━━━━━━━━━━━━━━━━━━━━
事故编号: ACC20260707001
事故类型: 追尾事故 (置信度 92%)
风险等级: LOW
位置: G15 沈海高速 K1200+500 段（北向）
天气: 晴 | 路面: 干燥 | 占道: 1条车道

【资源推荐】
→ 推荐清障中心: 浦东清障一队 (距离约 3.2km)
→ 推荐清障人员: 王队长 (rescue1) — 可派出清障车 沪A·B3456
→ 预计到达时间: 8分钟

【处置优先级】
1. [立即] 放置警示标志，确保现场安全
2. [15分钟内] 清障车到达，拖离事故车辆
3. [30分钟内] 恢复车道通行
4. [后续] 联系交警处理定责

【风险评估】低风险，无次生事故隐患。',
 '追尾事故已指派浦东清障一队王队长前往处置，预计15分钟清理完毕。',
 'HYBRID', 'EXECUTED'),

-- 决策2: 车辆碰撞（高风险） → 嘉定清障救援队 陈师傅 + 救护车
(2, 3, 6, 2, 2,
 '【AI Agent 指挥调度分析】
━━━━━━━━━━━━━━━━━━━━
事故编号: ACC20260707002
事故类型: 车辆碰撞 (置信度 87%)
风险等级: HIGH
位置: G2 京沪高速 K980+200 段（南向）
天气: 多云 | 路面: 干燥 | 占道: 2条车道

【现场评估】
⚠️ 图像检测到人员受伤迹象 — 重伤2人（疑似骨折）
⚠️ 需120急救到场
⚠️ 货物部分散落，需吊车清理

【资源推荐】
→ 推荐清障中心: 嘉定清障救援队 (距离约 5.1km)
→ 推荐清障人员: 陈师傅 (rescue2) — 可派出清障车 沪B·12345 + 救护车 沪B·D4567
→ 预计到达时间: 12分钟（清障车）/ 10分钟（救护车）

【处置优先级】
1. [立即] 封闭事故车道，设置变道引导
2. [10分钟内] 120急救到达，优先救治重伤员
3. [20分钟内] 吊车清理散落货物
4. [90分钟内] 全面恢复通行

【次生风险提示】
- 封闭2条车道可能导致后方拥堵约60分钟
- 建议启动周边路段交通疏导预案',
 '高风险碰撞事故，指派嘉定清障救援队陈师傅携带清障车和救护车前往，120已同步调度。',
 'HYBRID', 'ISSUED'),

-- 决策3: 货物散落（严重风险） → 浦东清障一队 + 闵行工程队
(4, 3, 4, 1, 4,
 '【AI Agent 指挥调度分析】
━━━━━━━━━━━━━━━━━━━━
事故编号: ACC20260707004
事故类型: 货物散落 (置信度 94%)
风险等级: CRITICAL
位置: S20 外环高速 K85+300 段（内圈）
天气: 晴 | 路面: 干燥 | 占道: 3条车道（全部）

【现场评估】
🔴 严重阻断 — 货物完全阻断3条车道
🔴 约3吨重物（疑似建材），需大型吊车
🔴 货主被货物擦伤，已安排120检查

【资源推荐】
→ 主清障中心: 浦东清障一队 (距离约 6.8km)
→ 辅助: 闵行工程清障队 (距离约 4.2km)
→ 推荐车辆: 工程车 沪A·C7890 + 备用吊车
→ 预计到达时间: 15分钟（工程车）/ 18分钟（吊车）

【处置优先级】
1. [立即] 封闭全部事故车道，引导车辆绕行
2. [15分钟内] 工程车到场，开始货物清理
3. [30分钟内] 至少恢复1条车道通行
4. [120分钟内] 全面恢复3条车道

【影响评估】
- 预计拥堵90分钟，影响范围可达周边3条快速路
- 建议向市民发布绕行提示（延安高架/中环路替代方案）',
 '严重货物散落阻断全部车道，指派浦东清障一队（主）和闵行工程队（辅）联合处置，同步发布市民绕行建议。',
 'AUTO', 'ISSUED'),

-- 决策4: 车辆自燃 → 嘉定清障救援队
(5, 3, 6, 2, 5,
 '【AI Agent 指挥调度分析】
━━━━━━━━━━━━━━━━━━━━
事故编号: ACC20260707005
事故类型: 车辆自燃 (置信度 89%)
风险等级: HIGH
位置: 南北高架 北京路段（南向）
天气: 晴 | 路面: 干燥 | 占道: 2条车道

【现场评估】
⚠️ 火势已控制，需确认无复燃风险
⚠️ 车辆已烧毁，需平板拖车
⚠️ 高峰时段快速路，影响范围大

【资源推荐】
→ 推荐清障中心: 嘉定清障救援队 (距离约 8.5km)
→ 推荐清障人员: 陈师傅 (rescue2) — 平板拖车 沪B·12345
→ 预计到达时间: 18分钟

【处置优先级】
1. [已确认] 消防已到场控制火势
2. [立即] 确认无复燃风险和漏油隐患
3. [20分钟内] 平板拖车到达拖离烧毁车辆
4. [40分钟内] 清理路面，恢复通行

【安全提示】
- 注意漏油导致的次生打滑风险
- 建议待消防确认安全后再进行清障作业',
 '车辆自燃已由消防控制，指派嘉定清障救援队陈师傅携带平板拖车前往清理。',
 'HYBRID', 'DRAFT');


-- ============================================================
-- 9. 操作日志 (10条)
-- ============================================================
INSERT INTO operation_logs (operator_user_id, operation_type, object_type, object_id, ip_address, detail) VALUES
(1, '用户登录',      '认证模块', '1', '192.168.1.101', '用户 张警官(FIELD_OFFICER) 登录系统'),
(1, 'CREATE_INCIDENT','事故模块', '1', '192.168.1.101', '上报事故 ACC20260707001：追尾事故 - G15沈海高速'),
(3, '用户登录',      '认证模块', '3', '192.168.1.102', '用户 李指挥(COMMAND_CENTER) 登录系统'),
(3, 'CREATE_DISPATCH','调度模块', '1', '192.168.1.102', '为事故 ACC20260707001 创建调度任务 TASK-20260707-001'),
(3, 'CREATE_DECISION','指挥中心', '1', '192.168.1.102', '创建指挥决策: ACC20260707001 → 浦东清障一队'),
(4, 'UPDATE_STATUS',  '调度模块', '1', '192.168.1.103', 'TASK-20260707-001 状态变更: DISPATCHED → DEPARTED'),
(4, 'UPDATE_STATUS',  '调度模块', '1', '192.168.1.103', 'TASK-20260707-001 状态变更: DEPARTED → ARRIVED'),
(4, 'UPDATE_STATUS',  '调度模块', '1', '192.168.1.103', 'TASK-20260707-001 状态变更: ARRIVED → PROCESSING'),
(4, 'UPDATE_STATUS',  '调度模块', '1', '192.168.1.103', 'TASK-20260707-001 状态变更: PROCESSING → COMPLETED，反馈: 现场清理完毕'),
(6, '用户管理',      '系统管理', '6', '192.168.1.104', '管理员 赵管理 查看用户列表');


-- ============================================================
-- 10. 系统数据字典 (6条) — 道路/事故类型/风险规则
-- ============================================================
INSERT INTO system_data (category, code, name, config_value, description, enabled, sort_order) VALUES
('ROAD', 'EXPRESSWAY', '高速公路',
 '{"level":"HIGHWAY","defaultSpeedLimit":120,"lanes":3}',
 '道路等级: 高速公路', 1, 10),

('ROAD', 'URBAN_MAIN', '城市主干路',
 '{"level":"ARTERIAL","defaultSpeedLimit":60,"lanes":4}',
 '道路等级: 城市主干路', 1, 20),

('ROAD', 'ELEVATED', '城市高架/快速路',
 '{"level":"EXPRESSWAY","defaultSpeedLimit":80,"lanes":3}',
 '道路等级: 城市高架快速路', 1, 30),

('ACCIDENT_TYPE', 'REAR_END', '追尾事故',
 '{"defaultRisk":"MEDIUM","typicalRecoveryMins":35}',
 '事故类型: 追尾碰撞', 1, 10),

('ACCIDENT_TYPE', 'ROLLOVER', '车辆侧翻',
 '{"defaultRisk":"HIGH","typicalRecoveryMins":90,"requiresCrane":true}',
 '事故类型: 车辆侧翻', 1, 20),

('RISK_RULE', 'HIGH_RISK_LANE_BLOCK', '多车道占用高风险规则',
 '{"occupiedLanesGte":2,"riskLevel":"HIGH","autoSupportRequired":true}',
 '自动支援规则: 占用≥2条车道时触发高风险', 1, 10);


-- ============================================================
-- 11. 通知记录 (2条)
-- ============================================================
INSERT INTO notification_records (receiver_user_id, channel, title, content, status, sent_at) VALUES
(4, 'SMS', '新调度任务 TASK-20260707-001',
 '事故 ACC20260707001（追尾事故）已指派您前往处置，请立即前往 G15 沈海高速 K1200+500 段（北向）。预计距离 3.2km，约8分钟到达。',
 'SENT', '2026-07-07 10:02:00'),

(6, 'SMS', '新调度任务 TASK-20260707-002',
 '事故 ACC20260707002（车辆碰撞）已指派您前往处置，请携带破拆工具立即前往 G2 京沪高速 K980+200 段（南向）。预计距离 5.1km，约12分钟到达。现场有重伤员，请注意安全。',
 'SENT', '2026-07-07 10:11:00');

-- ============================================================
-- 交通事故风险智能识别与调度系统 - 演示种子数据
-- 密码均为 123456 (SHA-256)
-- ============================================================
=======
COMMIT;
>>>>>>> d874f3e4ce1ef758c836de1a119d3c96eb622dd0
