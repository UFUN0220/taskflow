-- TaskFlow stage 1 schema.
-- Business writes, seed accounts and permission assignment are implemented in later stages.

CREATE TABLE `sys_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT NULL,
    `department_code` VARCHAR(64) NOT NULL,
    `department_name` VARCHAR(128) NOT NULL,
    `path` VARCHAR(1024) NOT NULL DEFAULT '/',
    `level` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_department_code` UNIQUE (`department_code`),
    CONSTRAINT `uk_department_parent_name` UNIQUE (`parent_id`, `department_name`),
    CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_department` (`id`),
    CONSTRAINT `chk_department_level` CHECK (`level` >= 1),
    CONSTRAINT `chk_department_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_department_parent_status`
    ON `sys_department` (`parent_id`, `status`);

CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `employee_no` VARCHAR(64) NOT NULL,
    `display_name` VARCHAR(128) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `department_id` BIGINT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `last_login_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_username` UNIQUE (`username`),
    CONSTRAINT `uk_user_employee_no` UNIQUE (`employee_no`),
    CONSTRAINT `fk_user_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
    CONSTRAINT `chk_user_status` CHECK (`status` IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_user_department_status`
    ON `sys_user` (`department_id`, `status`);

CREATE TABLE `sys_position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `position_code` VARCHAR(64) NOT NULL,
    `position_name` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_position_code` UNIQUE (`position_code`),
    CONSTRAINT `chk_position_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `sys_user_position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `position_id` BIGINT NOT NULL,
    `is_primary` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_position` UNIQUE (`user_id`, `position_id`),
    CONSTRAINT `fk_user_position_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `fk_user_position_position` FOREIGN KEY (`position_id`) REFERENCES `sys_position` (`id`),
    CONSTRAINT `chk_user_position_primary` CHECK (`is_primary` IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_user_position_position`
    ON `sys_user_position` (`position_id`, `user_id`);

CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_code` VARCHAR(64) NOT NULL,
    `role_name` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `built_in` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_role_code` UNIQUE (`role_code`),
    CONSTRAINT `chk_role_status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT `chk_role_built_in` CHECK (`built_in` IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `sys_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `permission_code` VARCHAR(128) NOT NULL,
    `permission_name` VARCHAR(128) NOT NULL,
    `resource_type` VARCHAR(64) NOT NULL,
    `action` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_permission_code` UNIQUE (`permission_code`),
    CONSTRAINT `uk_permission_resource_action` UNIQUE (`resource_type`, `action`),
    CONSTRAINT `chk_permission_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_role` UNIQUE (`user_id`, `role_id`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_user_role_role`
    ON `sys_user_role` (`role_id`, `user_id`);

CREATE TABLE `sys_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_role_permission` UNIQUE (`role_id`, `permission_id`),
    CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
    CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_role_permission_permission`
    ON `sys_role_permission` (`permission_id`, `role_id`);

CREATE TABLE `sys_role_data_scope` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `scope_type` VARCHAR(32) NOT NULL,
    `scope_config` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_role_scope_type` UNIQUE (`role_id`, `scope_type`),
    CONSTRAINT `fk_role_scope_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
    CONSTRAINT `chk_role_scope_type` CHECK (`scope_type` IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_AND_CHILDREN', 'PROJECT', 'ALL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `sys_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_code` VARCHAR(64) NOT NULL,
    `project_name` VARCHAR(200) NOT NULL,
    `department_id` BIGINT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `start_at` DATETIME(3) NULL,
    `end_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_project_code` UNIQUE (`project_code`),
    CONSTRAINT `fk_project_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
    CONSTRAINT `fk_project_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_project_status` CHECK (`status` IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT `chk_project_time_range` CHECK (`end_at` IS NULL OR `start_at` IS NULL OR `end_at` >= `start_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_project_owner_status`
    ON `sys_project` (`owner_user_id`, `status`);

CREATE TABLE `sys_project_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `member_role` VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_project_member_user` UNIQUE (`project_id`, `user_id`),
    CONSTRAINT `fk_project_member_project` FOREIGN KEY (`project_id`) REFERENCES `sys_project` (`id`),
    CONSTRAINT `fk_project_member_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_project_member_role` CHECK (`member_role` IN ('MANAGER', 'MEMBER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_project_member_user_role`
    ON `sys_project_member` (`user_id`, `member_role`, `project_id`);

CREATE TABLE `task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_no` VARCHAR(64) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT NULL,
    `project_id` BIGINT NULL,
    `department_id` BIGINT NULL,
    `creator_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `priority` VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    `due_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_task_no` UNIQUE (`task_no`),
    CONSTRAINT `fk_task_project` FOREIGN KEY (`project_id`) REFERENCES `sys_project` (`id`),
    CONSTRAINT `fk_task_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
    CONSTRAINT `fk_task_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_task_status` CHECK (`status` IN ('DRAFT', 'PENDING_ACCEPTANCE', 'IN_PROGRESS', 'PENDING_REVIEW', 'REJECTED', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT `chk_task_priority` CHECK (`priority` IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_task_creator_status_created`
    ON `task` (`creator_id`, `status`, `created_at`);

CREATE INDEX `idx_task_project_status_due`
    ON `task` (`project_id`, `status`, `due_at`);

CREATE INDEX `idx_task_department_status_due`
    ON `task` (`department_id`, `status`, `due_at`);

CREATE INDEX `idx_task_status_due`
    ON `task` (`status`, `due_at`);

CREATE TABLE `task_assignee` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `assignee_type` VARCHAR(32) NOT NULL,
    `assigned_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `accepted_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_task_assignee_user` UNIQUE (`task_id`, `user_id`),
    CONSTRAINT `fk_task_assignee_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_assignee_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_task_assignee_type` CHECK (`assignee_type` IN ('PRIMARY', 'COLLABORATOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_task_assignee_user_type`
    ON `task_assignee` (`user_id`, `assignee_type`, `task_id`);

CREATE INDEX `idx_task_assignee_task_type`
    ON `task_assignee` (`task_id`, `assignee_type`);

CREATE TABLE `task_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `author_user_id` BIGINT NOT NULL,
    `comment_type` VARCHAR(32) NOT NULL DEFAULT 'USER',
    `content` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_task_comment_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_comment_author` FOREIGN KEY (`author_user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_task_comment_type` CHECK (`comment_type` IN ('USER', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_task_comment_task_created`
    ON `task_comment` (`task_id`, `created_at`);

CREATE TABLE `task_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `uploader_user_id` BIGINT NOT NULL,
    `storage_bucket` VARCHAR(128) NOT NULL,
    `object_key` VARCHAR(512) NOT NULL,
    `original_filename` VARCHAR(255) NOT NULL,
    `content_type` VARCHAR(128) NOT NULL,
    `size_bytes` BIGINT NOT NULL,
    `checksum` VARCHAR(128) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_attachment_object_key` UNIQUE (`storage_bucket`, `object_key`),
    CONSTRAINT `fk_task_attachment_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_attachment_uploader` FOREIGN KEY (`uploader_user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_attachment_size` CHECK (`size_bytes` >= 0),
    CONSTRAINT `chk_attachment_status` CHECK (`status` IN ('UPLOADING', 'AVAILABLE', 'DELETED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_task_attachment_task_created`
    ON `task_attachment` (`task_id`, `created_at`);

CREATE TABLE `task_operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `operator_id` BIGINT NOT NULL,
    `operation_type` VARCHAR(64) NOT NULL,
    `from_status` VARCHAR(32) NULL,
    `to_status` VARCHAR(32) NULL,
    `before_data` JSON NULL,
    `after_data` JSON NULL,
    `operation_note` VARCHAR(500) NULL,
    `trace_id` VARCHAR(64) NULL,
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_task_operation_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_operation_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_task_operation_task_occurred`
    ON `task_operation_log` (`task_id`, `occurred_at`);

CREATE INDEX `idx_task_operation_operator_occurred`
    ON `task_operation_log` (`operator_id`, `occurred_at`);

CREATE TABLE `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `notification_type` VARCHAR(64) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `aggregate_type` VARCHAR(64) NULL,
    `aggregate_id` BIGINT NULL,
    `read_at` DATETIME(3) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_notification_status` CHECK (`status` IN ('UNREAD', 'READ', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_notification_user_status_created`
    ON `notification` (`user_id`, `status`, `created_at`);

CREATE TABLE `reminder_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `reminder_type` VARCHAR(64) NOT NULL,
    `trigger_at` DATETIME(3) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    `last_emitted_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_reminder_task_type_trigger` UNIQUE (`task_id`, `reminder_type`, `trigger_at`),
    CONSTRAINT `fk_reminder_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `chk_reminder_status` CHECK (`status` IN ('PLANNED', 'EMITTED', 'CANCELLED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_reminder_status_trigger`
    ON `reminder_plan` (`status`, `trigger_at`);

CREATE INDEX `idx_reminder_task_status`
    ON `reminder_plan` (`task_id`, `status`);

CREATE TABLE `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `trace_id` VARCHAR(64) NULL,
    `operator_id` BIGINT NULL,
    `resource_type` VARCHAR(64) NOT NULL,
    `resource_id` VARCHAR(128) NULL,
    `action` VARCHAR(64) NOT NULL,
    `result` VARCHAR(32) NOT NULL,
    `request_method` VARCHAR(16) NULL,
    `request_uri` VARCHAR(512) NULL,
    `ip_address` VARCHAR(64) NULL,
    `detail_json` JSON NULL,
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `chk_audit_result` CHECK (`result` IN ('SUCCESS', 'FAILURE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_audit_trace`
    ON `audit_log` (`trace_id`);

CREATE INDEX `idx_audit_operator_occurred`
    ON `audit_log` (`operator_id`, `occurred_at`);

CREATE INDEX `idx_audit_resource_occurred`
    ON `audit_log` (`resource_type`, `resource_id`, `occurred_at`);
