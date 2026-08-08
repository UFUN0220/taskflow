-- Stage 10 notification delivery idempotency and dead-letter records.

ALTER TABLE `notification`
    ADD COLUMN `source_message_id` VARCHAR(128) NULL AFTER `notification_type`;

CREATE UNIQUE INDEX `uk_notification_source_message_user`
    ON `notification` (`source_message_id`, `user_id`);

CREATE TABLE `notification_dead_letter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(128) NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `trace_id` VARCHAR(64) NULL,
    `payload_json` JSON NOT NULL,
    `plan_id` BIGINT NULL,
    `task_id` BIGINT NULL,
    `error_reason` VARCHAR(1000) NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'RETRYING',
    `last_failed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `replayed_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    `created_by` BIGINT NULL,
    `updated_by` BIGINT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_notification_dead_message` UNIQUE (`message_id`),
    CONSTRAINT `chk_notification_dead_status` CHECK (`status` IN ('RETRYING', 'DEAD', 'REPLAYED')),
    CONSTRAINT `chk_notification_dead_retry_count` CHECK (`retry_count` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_notification_dead_status_failed`
    ON `notification_dead_letter` (`status`, `last_failed_at`);

CREATE INDEX `idx_notification_dead_task_failed`
    ON `notification_dead_letter` (`task_id`, `last_failed_at`);

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('notification:read', '查看站内通知', 'NOTIFICATION', 'READ', 'ACTIVE', 0, 0),
    ('notification:write', '处理站内通知', 'NOTIFICATION', 'WRITE', 'ACTIVE', 0, 0),
    ('notification:dead-letter:read', '查看通知死信', 'NOTIFICATION_DEAD_LETTER', 'READ', 'ACTIVE', 0, 0),
    ('notification:dead-letter:replay', '补偿通知死信', 'NOTIFICATION_DEAD_LETTER', 'REPLAY', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` IN ('system_admin', 'employee', 'department_manager', 'project_manager')
  AND p.`permission_code` IN ('notification:read', 'notification:write')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'system_admin'
  AND p.`permission_code` IN ('notification:dead-letter:read', 'notification:dead-letter:replay')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
