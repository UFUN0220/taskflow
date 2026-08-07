# 阶段 4：角色、组织与数据范围管理

## 功能边界

阶段 4 聚焦后台管理闭环，不提前实现任务、项目、消息和文件业务：

- 角色：查询、创建和更新自定义角色；权限以 `sys_permission.permission_code` 校验，角色权限和数据范围关系在一个事务中替换；内置角色不可修改。
- 部门：创建部门时生成树路径，更新部门名称/状态时要求 `version`；本阶段不支持移动部门，避免未实现子树路径重算。
- 用户：创建用户、停用/锁定用户、分配角色；用户名和工号沿用数据库唯一约束，并对写操作执行后端权限校验。
- 数据范围：用户查询使用角色数据范围解析器。多个角色同时存在时取最宽范围；`PROJECT` 在用户查询中降级为 `SELF`，待项目/任务查询适配器实现后再扩展。

## API

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/roles` | `role:read` | 查询启用角色及权限/数据范围 |
| POST/PUT | `/api/roles` | `role:write` + `data_scope:write` | 创建或更新自定义角色及其数据范围 |
| GET | `/api/users` | `user:read` | 按当前用户数据范围分页查询 |
| POST | `/api/users` | `user:write` | 创建用户并分配角色 |
| PATCH | `/api/users/{id}/status` | `user:write` | 更新用户状态，需要 version |
| PUT | `/api/users/{id}/roles` | `user:role:write` | 替换用户角色，需要 version |
| GET | `/api/departments/tree` | `department:read` | 查询启用部门树 |
| POST/PUT | `/api/departments` | `department:write` | 创建或更新部门 |

## Flyway V4

`V4__seed_management_permissions_and_scopes.sql` 只负责幂等种子数据：管理权限、部门主管/项目经理角色、系统管理员和内置角色的数据范围。它不写入任何明文密码，也不修改已应用的 V1-V3 文件。
