-- Stage 8 comment and attachment permissions.

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('task:comment:read', '查看任务评论', 'TASK_COMMENT_READ', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:comment:create', '发表评论', 'TASK_COMMENT_CREATE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:attachment:read', '查看任务附件', 'TASK_ATTACHMENT_READ', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:attachment:create', '上传任务附件', 'TASK_ATTACHMENT_CREATE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:attachment:delete', '删除任务附件', 'TASK_ATTACHMENT_DELETE', 'EXECUTE', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` IN ('system_admin', 'employee', 'department_manager', 'project_manager')
  AND p.`permission_code` IN ('task:comment:read', 'task:comment:create',
                              'task:attachment:read', 'task:attachment:create')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` IN ('system_admin', 'department_manager', 'project_manager')
  AND p.`permission_code` = 'task:attachment:delete'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
