-- ============================================================
-- 算法3 道路恢复推荐 后端接入增量脚本
-- 适用于已经存在的 traffic_risk_db。
-- 如果使用 database/init.sql 全新建库，则不需要再执行本文件。
-- ============================================================

USE `traffic_risk_db`;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$
CREATE PROCEDURE add_column_if_missing(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN ddl_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_value
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = ddl_value;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

-- ============================================================
-- prediction_results 表新增算法3道路恢复字段
-- ============================================================

CALL add_column_if_missing(
  'prediction_results',
  'recovery_recommendation',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_recommendation` VARCHAR(1200) NULL AFTER `raw_result`'
);

CALL add_column_if_missing(
  'prediction_results',
  'recovery_confidence',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_confidence` DOUBLE NULL AFTER `recovery_recommendation`'
);

CALL add_column_if_missing(
  'prediction_results',
  'recovery_level',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_level` VARCHAR(32) NULL AFTER `recovery_confidence`'
);

CALL add_column_if_missing(
  'prediction_results',
  'recovery_model_version',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_model_version` VARCHAR(160) NULL AFTER `recovery_level`'
);

CALL add_column_if_missing(
  'prediction_results',
  'recovery_trace_id',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_trace_id` VARCHAR(80) NULL AFTER `recovery_model_version`'
);

CALL add_column_if_missing(
  'prediction_results',
  'recovery_key_factors',
  'ALTER TABLE `prediction_results` ADD COLUMN `recovery_key_factors` VARCHAR(1000) NULL AFTER `recovery_trace_id`'
);

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- 验证迁移结果
SELECT
  COLUMN_NAME,
  COLUMN_TYPE,
  IS_NULLABLE,
  COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'prediction_results'
  AND COLUMN_NAME LIKE 'recovery%'
ORDER BY ORDINAL_POSITION;
