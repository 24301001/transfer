-- ============================================================
-- init.sql
-- 閬撹矾浜ら€氫簨鏁呴闄╅浼颁笌鍚庢灉棰勬祴骞冲彴
-- MySQL 8.x
-- 渚濇嵁褰撳墠 Spring Boot / JPA 瀹炰綋閲嶅啓
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `traffic_risk_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `traffic_risk_db`;

-- 鎸変緷璧栧叧绯诲弽鍚戝垹闄ゆ棫琛?DROP TABLE IF EXISTS `dispatch_decisions`;
DROP TABLE IF EXISTS `clearance_rescue_advices`;
DROP TABLE IF EXISTS `dispatch_tasks`;
DROP TABLE IF EXISTS `incident_attachments`;
DROP TABLE IF EXISTS `prediction_results`;
DROP TABLE IF EXISTS `notification_records`;
DROP TABLE IF EXISTS `operation_logs`;
DROP TABLE IF EXISTS `emergency_vehicles`;
DROP TABLE IF EXISTS `rescue_centers`;
DROP TABLE IF EXISTS `incidents`;
DROP TABLE IF EXISTS `system_data`;
DROP TABLE IF EXISTS `items`;
DROP TABLE IF EXISTS `app_users`;

-- ============================================================
-- 1. 鐢ㄦ埛璐︽埛
-- 瀵瑰簲瀹炰綋锛歎serAccount
-- ============================================================
CREATE TABLE `app_users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `full_name` VARCHAR(64) NOT NULL,
  `username` VARCHAR(64) NOT NULL,
  `phone` VARCHAR(32) NULL,
  `email` VARCHAR(128) NULL,
  `role` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `email_verified` TINYINT(1) NOT NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_app_users_username` (`username`),
  UNIQUE KEY `uk_app_users_email` (`email`),

  KEY `idx_app_users_role_status` (`role`, `status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. 浜嬫晠浜嬩欢
-- 瀵瑰簲瀹炰綋锛欼ncident
-- ============================================================
CREATE TABLE `incidents` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `incident_no` VARCHAR(40) NOT NULL,

  `location_name` VARCHAR(160) NOT NULL,
  `address` VARCHAR(255) NULL,

  `longitude` DOUBLE NULL,
  `latitude` DOUBLE NULL,

  `coordinate_type` VARCHAR(16) NULL,

  `baidu_longitude` DOUBLE NULL,
  `baidu_latitude` DOUBLE NULL,

  `map_formatted_address` VARCHAR(255) NULL,
  `map_semantic_description` VARCHAR(500) NULL,

  `road_name` VARCHAR(80) NULL,

  `initial_accident_type` VARCHAR(80) NULL,
  `confirmed_accident_type` VARCHAR(80) NULL,
  `scene_labels` VARCHAR(500) NULL,

  `description` VARCHAR(1000) NOT NULL,

  `occupied_lanes` INT NULL,
  `traffic_flow` INT NULL,
  `people_flow` INT NULL,

  `people_involved` INT NULL,
  `injured_count` INT NULL,
  `injury_reported` TINYINT(1) NOT NULL DEFAULT 0,
  `injury_estimate` VARCHAR(500) NULL,

  `weather` VARCHAR(40) NULL,
  `road_level` VARCHAR(40) NULL,
  `road_status` VARCHAR(80) NULL,

  `status` VARCHAR(32) NOT NULL,
  `risk_level` VARCHAR(32) NULL,

  `predicted_congestion_minutes` INT NULL,
  `predicted_recovery_minutes` INT NULL,

  `confidence` DOUBLE NULL,

  `suggestion` VARCHAR(1000) NULL,
  `explanation` VARCHAR(1500) NULL,

  `support_required` TINYINT(1) NOT NULL,
  `support_reason` VARCHAR(500) NULL,

  `support_decision_manual` TINYINT(1) NOT NULL,
  `support_decision_by_user_id` BIGINT NULL,
  `support_decision_at` DATETIME(6) NULL,

  `citizen_immediate_advice` VARCHAR(1000) NULL,

  `casualty_detected` TINYINT(1) NOT NULL,

  `estimated_police_arrival_minutes` INT NULL,
  `police_arrival_text` VARCHAR(160) NULL,

  `report_user_id` BIGINT NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_incidents_incident_no` (`incident_no`),

  KEY `idx_incidents_status` (`status`),
  KEY `idx_incidents_risk_level` (`risk_level`),
  KEY `idx_incidents_report_user_id` (`report_user_id`),
  KEY `idx_incidents_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. 浜嬫晠闄勪欢
-- 瀵瑰簲瀹炰綋锛欼ncidentAttachment
-- ============================================================
CREATE TABLE `incident_attachments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `incident_id` BIGINT NOT NULL,

  `file_name` VARCHAR(255) NOT NULL,
  `original_filename` VARCHAR(255) NOT NULL,

  `content_type` VARCHAR(80) NULL,

  `attachment_type` VARCHAR(20) NOT NULL,

  `file_path` VARCHAR(500) NOT NULL,
  `file_size` BIGINT NULL,

  `uploaded_by` BIGINT NULL,

  `recognition_status` VARCHAR(32) NOT NULL,

  `ai_detected_types` VARCHAR(200) NULL,
  `ai_detection_json` LONGTEXT NULL,

  `reviewed` TINYINT(1) NOT NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_incident_attachments_incident_id` (`incident_id`),
  KEY `idx_incident_attachments_uploaded_by` (`uploaded_by`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. 棰勬祴缁撴灉
-- 瀵瑰簲瀹炰綋锛歅redictionResult
-- ============================================================
CREATE TABLE `prediction_results` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `incident_id` BIGINT NOT NULL,

  `accident_type` VARCHAR(80) NOT NULL,

  `risk_level` VARCHAR(32) NOT NULL,
  `risk_score` DOUBLE NULL,

  `congestion_duration_minutes` INT NULL,
  `recovery_duration_minutes` INT NULL,

  `confidence` DOUBLE NULL,

  `model_version` VARCHAR(200) NULL,

  `suggestions` VARCHAR(1000) NULL,
  `explanation` VARCHAR(1500) NULL,
  `risk_factors` VARCHAR(1000) NULL,

  `image_evidence` VARCHAR(1000) NULL,
  `evidence_summary` VARCHAR(1000) NULL,

  `data_module_trace_id` VARCHAR(80) NULL,

  `raw_result` VARCHAR(3000) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_prediction_results_incident_id_created_at`
    (`incident_id`, `created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 5. 璋冨害浠诲姟
-- 瀵瑰簲瀹炰綋锛欴ispatchTask
-- ============================================================
CREATE TABLE `dispatch_tasks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `task_no` VARCHAR(40) NOT NULL,

  `incident_id` BIGINT NOT NULL,

  `task_type` VARCHAR(32) NOT NULL,

  `receiver_user_id` BIGINT NULL,
  `assigned_by_user_id` BIGINT NULL,

  `status` VARCHAR(32) NOT NULL,

  `vehicle_required` TINYINT(1) NULL,
  `vehicle_type` VARCHAR(80) NULL,

  `location_name` VARCHAR(160) NULL,

  `risk_level` VARCHAR(32) NULL,

  `advice` VARCHAR(1000) NULL,
  `feedback` VARCHAR(1000) NULL,

  `completed_at` DATETIME(6) NULL,

  `emergency_vehicle_id` BIGINT NULL,
  `emergency_vehicle_no` VARCHAR(40) NULL,
  `emergency_vehicle_name` VARCHAR(80) NULL,

  `vehicle_start_longitude` DOUBLE NULL,
  `vehicle_start_latitude` DOUBLE NULL,

  `vehicle_start_baidu_longitude` DOUBLE NULL,
  `vehicle_start_baidu_latitude` DOUBLE NULL,

  `incident_target_longitude` DOUBLE NULL,
  `incident_target_latitude` DOUBLE NULL,

  `incident_target_baidu_longitude` DOUBLE NULL,
  `incident_target_baidu_latitude` DOUBLE NULL,

  `dispatch_distance_km` DOUBLE NULL,
  `dispatch_speed_kmh` DOUBLE NULL,

  `estimated_arrival_minutes` INT NULL,

  `departed_at` DATETIME(6) NULL,
  `arrived_at` DATETIME(6) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_dispatch_tasks_task_no` (`task_no`),

  KEY `idx_dispatch_tasks_incident_id` (`incident_id`),

  KEY `idx_dispatch_tasks_receiver_status`
    (`receiver_user_id`, `status`),

  KEY `idx_dispatch_tasks_status` (`status`),

  KEY `idx_dispatch_tasks_vehicle_status`
    (`emergency_vehicle_id`, `status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 6. 搴旀€ヨ溅杈?-- 瀵瑰簲瀹炰綋锛欵mergencyVehicle
-- ============================================================
CREATE TABLE `emergency_vehicles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `vehicle_no` VARCHAR(40) NOT NULL,
  `vehicle_name` VARCHAR(80) NOT NULL,

  `vehicle_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,

  `longitude` DOUBLE NULL,
  `latitude` DOUBLE NULL,

  `coordinate_type` VARCHAR(16) NULL,

  `baidu_longitude` DOUBLE NULL,
  `baidu_latitude` DOUBLE NULL,

  `speed_kmh` DOUBLE NULL,

  `current_address` VARCHAR(255) NULL,

  `current_task_id` BIGINT NULL,

  `remark` VARCHAR(500) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_emergency_vehicles_vehicle_no` (`vehicle_no`),

  KEY `idx_emergency_vehicles_type_status`
    (`vehicle_type`, `status`),

  KEY `idx_emergency_vehicles_status` (`status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 7. AI 娓呴殰鏁戞彺寤鸿
-- 瀵瑰簲瀹炰綋锛欳learanceRescueAdvice
-- ============================================================
CREATE TABLE `clearance_rescue_advices` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `incident_id` BIGINT NOT NULL,

  `prediction_result_id` BIGINT NOT NULL,

  `ai_advice` VARCHAR(4000) NOT NULL,
  `final_advice` VARCHAR(4000) NULL,

  `status` VARCHAR(32) NOT NULL,

  `generation_source` VARCHAR(32) NOT NULL,

  `modified_by_command` TINYINT(1) NULL,

  `confirmed_by_user_id` BIGINT NULL,
  `confirmed_at` DATETIME(6) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_clearance_advices_incident_created`
    (`incident_id`, `created_at`),

  KEY `idx_clearance_advices_incident_status_confirmed`
    (`incident_id`, `status`, `confirmed_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 8. 鎸囨尌璋冨害鍐崇瓥
-- 瀵瑰簲瀹炰綋锛欴ispatchDecision
-- ============================================================
CREATE TABLE `dispatch_decisions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `incident_id` BIGINT NOT NULL,

  `command_user_id` BIGINT NOT NULL,

  `rescue_user_id` BIGINT NULL,
  `rescue_center_id` BIGINT NULL,

  `dispatch_task_id` BIGINT NULL,

  `agent_content` LONGTEXT NULL,

  `decision_summary` VARCHAR(1000) NULL,

  `decision_type` VARCHAR(16) NOT NULL,

  `status` VARCHAR(16) NOT NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_dispatch_decisions_incident_created`
    (`incident_id`, `created_at`),

  KEY `idx_dispatch_decisions_command_created`
    (`command_user_id`, `created_at`),

  KEY `idx_dispatch_decisions_rescue_created`
    (`rescue_user_id`, `created_at`),

  KEY `idx_dispatch_decisions_task_id`
    (`dispatch_task_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 9. 鏁戞彺涓績
-- 瀵瑰簲瀹炰綋锛歊escueCenter
-- ============================================================
CREATE TABLE `rescue_centers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `name` VARCHAR(120) NOT NULL,

  `center_type` VARCHAR(32) NOT NULL,

  `address` VARCHAR(255) NULL,

  `longitude` DOUBLE NULL,
  `latitude` DOUBLE NULL,

  `phone` VARCHAR(32) NULL,

  `status` VARCHAR(32) NOT NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_rescue_centers_status` (`status`),
  KEY `idx_rescue_centers_type` (`center_type`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 10. 閫氱煡璁板綍
-- 瀵瑰簲瀹炰綋锛歂otificationRecord
-- ============================================================
CREATE TABLE `notification_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `receiver_user_id` BIGINT NULL,

  `channel` VARCHAR(32) NOT NULL,

  `title` VARCHAR(160) NOT NULL,

  `content` VARCHAR(1000) NOT NULL,

  `status` VARCHAR(32) NOT NULL,

  `failure_reason` VARCHAR(500) NULL,

  `sent_at` DATETIME(6) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_notification_records_receiver_user_id`
    (`receiver_user_id`),

  KEY `idx_notification_records_status`
    (`status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 11. 鎿嶄綔鏃ュ織
-- 瀵瑰簲瀹炰綋锛歄perationLog
-- ============================================================
CREATE TABLE `operation_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `operator_user_id` BIGINT NULL,

  `operation_type` VARCHAR(80) NOT NULL,

  `object_type` VARCHAR(80) NOT NULL,

  `object_id` VARCHAR(80) NULL,

  `ip_address` VARCHAR(80) NULL,

  `detail` VARCHAR(1000) NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  KEY `idx_operation_logs_operation_type_created`
    (`operation_type`, `created_at`),

  KEY `idx_operation_logs_operator_user_id`
    (`operator_user_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 12. 绯荤粺瀛楀吀涓庨厤缃?-- 瀵瑰簲瀹炰綋锛歋ystemData
--
-- 娉ㄦ剰锛?-- Java 瀛楁鍚嶄负 value
-- 浣嗛€氳繃 @Column(name = "config_value")
-- 鏄犲皠鍒版暟鎹簱涓殑 config_value
-- ============================================================
CREATE TABLE `system_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `category` VARCHAR(32) NOT NULL,

  `code` VARCHAR(80) NOT NULL,

  `name` VARCHAR(160) NOT NULL,

  `config_value` VARCHAR(2000) NULL,

  `description` VARCHAR(1000) NULL,

  `enabled` TINYINT(1) NOT NULL,

  `sort_order` INT NULL,

  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_system_data_category_code`
    (`category`, `code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 13. 绀轰緥 Item 琛?-- 瀵瑰簲瀹炰綋锛欼tem
-- ============================================================
CREATE TABLE `items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,

  `name` VARCHAR(255) NOT NULL,

  `description` VARCHAR(500) NULL,

  `created_at` DATETIME(6) NOT NULL,

  `updated_at` DATETIME(6) NOT NULL,

  PRIMARY KEY (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


SET FOREIGN_KEY_CHECKS = 1;

