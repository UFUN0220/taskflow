-- Stage 2 base audit fields.
-- Log tables remain append-only and intentionally do not receive logical-delete columns.

ALTER TABLE `sys_department`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_department_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_user`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_user_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_position`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_position_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_user_position`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 AFTER `updated_at`,
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_user_position_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_role`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_role_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_permission`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_permission_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_user_role`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 AFTER `created_at`,
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_user_role_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_role_permission`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 AFTER `created_at`,
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_role_permission_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_role_data_scope`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_role_scope_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_project`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_project_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `sys_project_member`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_project_member_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `task`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_task_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `task_assignee`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_task_assignee_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `task_comment`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_task_comment_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `task_attachment`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_task_attachment_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `notification`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_notification_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `reminder_plan`
    ADD COLUMN `created_by` BIGINT NULL AFTER `version`,
    ADD COLUMN `updated_by` BIGINT NULL AFTER `created_by`,
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `updated_by`,
    ADD CONSTRAINT `chk_reminder_deleted` CHECK (`deleted` IN (0, 1));
