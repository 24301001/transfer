-- ============================================================
-- 算法2后端接入增量脚本
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

CALL add_column_if_missing(
  'incidents',
  'scene_labels',
  'ALTER TABLE `incidents` ADD COLUMN `scene_labels` VARCHAR(500) NULL AFTER `confirmed_accident_type`'
);

CALL add_column_if_missing(
  'incidents',
  'injury_reported',
  'ALTER TABLE `incidents` ADD COLUMN `injury_reported` TINYINT(1) NOT NULL DEFAULT 0 AFTER `injured_count`'
);

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- 算法2会写入 prediction_results 既有字段：
-- risk_level, risk_score, congestion_duration_minutes,
-- recovery_duration_minutes, confidence, model_version,
-- suggestions, explanation, risk_factors, image_evidence,
-- evidence_summary, data_module_trace_id。
-- 这些字段在当前 init.sql 中已经存在，无需新增。

-- Expand model version for algorithm2 expert names
ALTER TABLE prediction_results MODIFY COLUMN model_version VARCHAR(200) NULL;
