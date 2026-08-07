-- Stage 4 management permissions, built-in role coverage, and data scopes.

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('user:write', '维护用户', 'USER', 'WRITE', 'ACTIVE', 0, 0),
    ('user:role:write', '分配用户角色', 'USER_ROLE', 'WRITE', 'ACTIVE', 0, 0),
    ('department:write', '维护部门', 'DEPARTMENT', 'WRITE', 'ACTIVE', 0, 0),
    ('role:write', '维护角色', 'ROLE', 'WRITE', 'ACTIVE', 0, 0),
    ('data_scope:write', '维护数据范围', 'DATA_SCOPE', 'WRITE', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role`
    (`role_code`, `role_name`, `status`, `built_in`, `version`, `deleted`)
VALUES
    ('department_manager', '部门主管', 'ACTIVE', 1, 0, 0),
    ('project_manager', '项目经理', 'ACTIVE', 1, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `status` = VALUES(`status`),
    `built_in` = VALUES(`built_in`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'system_admin'
  AND p.`permission_code` IN ('user:write', 'user:role:write', 'department:write', 'role:write', 'data_scope:write')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'department_manager'
  AND p.`permission_code` IN ('user:read', 'department:read', 'department:write')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'project_manager'
  AND p.`permission_code` IN ('user:read', 'department:read')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_data_scope` (`role_id`, `scope_type`, `scope_config`)
SELECT r.`id`, s.`scope_type`, NULL
FROM (
    SELECT 'system_admin' AS role_code, 'ALL' AS scope_type
    UNION ALL SELECT 'employee', 'SELF'
    UNION ALL SELECT 'department_manager', 'DEPARTMENT_AND_CHILDREN'
    UNION ALL SELECT 'project_manager', 'PROJECT'
) s
JOIN `sys_role` r ON r.`role_code` = s.`role_code`
ON DUPLICATE KEY UPDATE `scope_type` = VALUES(`scope_type`), `deleted` = 0;

