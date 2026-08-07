-- Stage 3 authentication and permission dictionary.
-- User credentials are bootstrapped by the application from environment variables,
-- never stored as a plaintext Flyway seed.

INSERT INTO `sys_permission`
    (`permission_code`, `permission_name`, `resource_type`, `action`, `status`, `version`, `deleted`)
VALUES
    ('auth:me', '查看当前用户', 'AUTH', 'READ_SELF', 'ACTIVE', 0, 0),
    ('user:read', '查看用户', 'USER', 'READ', 'ACTIVE', 0, 0),
    ('department:read', '查看部门', 'DEPARTMENT', 'READ', 'ACTIVE', 0, 0),
    ('role:read', '查看角色', 'ROLE', 'READ', 'ACTIVE', 0, 0),
    ('permission:read', '查看权限', 'PERMISSION', 'READ', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `sys_role`
    (`role_code`, `role_name`, `status`, `built_in`, `version`, `deleted`)
VALUES
    ('system_admin', '系统管理员', 'ACTIVE', 1, 0, 0),
    ('employee', '普通员工', 'ACTIVE', 1, 0, 0)
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
  AND p.`permission_code` IN ('auth:me', 'user:read', 'department:read', 'role:read', 'permission:read')
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'employee'
  AND p.`permission_code` = 'auth:me'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

