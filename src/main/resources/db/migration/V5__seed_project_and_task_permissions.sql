-- Stage 5 project and task permissions.

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('project:read', '查看项目', 'PROJECT_READ', 'EXECUTE', 'ACTIVE', 0, 0),
    ('project:write', '维护项目', 'PROJECT_WRITE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('project:member:write', '维护项目成员', 'PROJECT_MEMBER_WRITE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:create', '创建任务', 'TASK_CREATE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:read', '查看任务', 'TASK_READ', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:submit', '提交任务', 'TASK_SUBMIT', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:accept', '接受任务', 'TASK_ACCEPT', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:review', '提交任务审核', 'TASK_REVIEW', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:approve', '审核任务', 'TASK_APPROVE', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:assign', '分配任务', 'TASK_ASSIGN', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:cancel', '取消任务', 'TASK_CANCEL', 'EXECUTE', 'ACTIVE', 0, 0),
    ('task:archive', '归档任务', 'TASK_ARCHIVE', 'EXECUTE', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'system_admin'
  AND p.`permission_code` IN ('project:read', 'project:write', 'project:member:write',
                              'task:create', 'task:read', 'task:submit', 'task:accept',
                              'task:review', 'task:approve', 'task:assign', 'task:cancel', 'task:archive')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'employee'
  AND p.`permission_code` IN ('project:read', 'task:create', 'task:read', 'task:submit',
                              'task:accept', 'task:review', 'task:cancel')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'department_manager'
  AND p.`permission_code` IN ('project:read', 'task:read', 'task:approve', 'task:assign', 'task:cancel')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'project_manager'
  AND p.`permission_code` IN ('project:read', 'project:write', 'project:member:write',
                              'task:create', 'task:read', 'task:submit', 'task:accept',
                              'task:review', 'task:approve', 'task:assign', 'task:cancel', 'task:archive')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
