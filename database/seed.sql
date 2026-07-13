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


COMMIT;
