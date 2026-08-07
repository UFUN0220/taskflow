# 阶段 1 安全与权限边界

## RBAC 模型

```text
用户 ──< 用户角色 >── 角色 ──< 角色权限 >── 权限
                              │
                              └──< 角色数据范围
```

权限采用稳定编码，例如：

- `task:create`
- `task:assign`
- `task:update`
- `task:review`
- `task:archive`
- `user:manage`
- `role:manage`
- `audit:view`

功能权限回答“能不能执行某类动作”，数据范围回答“动作作用于哪些数据”。两者必须同时通过，不能只依赖前端按钮或用户提交的 `userId`。

## 数据范围

| 类型 | 语义 | 查询实现方向 |
| --- | --- | --- |
| `SELF` | 本人创建、负责或参与的记录 | 通过 creator/assignee 关系查询 |
| `DEPARTMENT` | 当前部门记录 | 使用任务 department_id 和当前用户部门 |
| `DEPARTMENT_AND_CHILDREN` | 当前部门及子部门 | 根据部门 path/树查询部门 ID 集合 |
| `PROJECT` | 用户参与或负责的项目 | 通过 `sys_project_member` 和 `project_id` 查询 |
| `ALL` | 全部授权数据 | 仅由显式角色配置授予 |

复杂数据范围使用明确的查询条件对象传递给 Repository，不把任意 SQL 片段写进 ThreadLocal，也不在 Controller 拼接用户输入。

## 当前阶段边界

阶段 5 已将 `PROJECT` 数据范围接入项目和任务查询：项目成员/负责人可见项目及其任务，其他范围继续通过部门或本人关系收敛。项目成员和任务负责人关系仍需通过后端服务校验，不能仅依赖前端传入的 ID。
