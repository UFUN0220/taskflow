-- Stage 6 task draft maintenance permissions.

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('task:update', '编辑任务草稿', 'TASK_UPDATE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:delete', '删除任务草稿', 'TASK_DELETE', 'EXECUTE', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` IN ('system_admin', 'employee', 'project_manager')
  AND p.`permission_code` IN ('task:update', 'task:delete')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
