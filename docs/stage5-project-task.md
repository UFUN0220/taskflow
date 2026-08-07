# 阶段 5：项目与任务核心生命周期

## 功能边界

本阶段只实现项目和任务的核心闭环：

- 项目：创建项目、按数据范围查询项目、添加或更新项目成员；项目创建者自动成为 `MANAGER`。
- 任务：创建草稿、查询任务、主负责人/协作人关系、转交任务。
- 生命周期：`DRAFT -> PENDING_ACCEPTANCE -> IN_PROGRESS -> PENDING_REVIEW -> COMPLETED -> ARCHIVED`，驳回后允许 `REJECTED -> IN_PROGRESS`，可从允许状态取消到 `CANCELLED`，取消任务可归档。
- 审计：每次状态命令和转交都在同一事务写入 `task_operation_log`，记录状态前后值、操作人和 Trace ID。

评论、附件、站内通知、提醒计划、MQ 和 MinIO 不在本阶段实现。

## API

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/projects` | `project:read` | 查询当前数据范围内项目 |
| POST | `/api/projects` | `project:write` | 创建项目 |
| PUT | `/api/projects/{id}/members` | `project:member:write` | 添加或更新项目成员 |
| POST | `/api/tasks` | `task:create` | 创建任务草稿 |
| GET | `/api/tasks` | `task:read` | 按数据范围分页查询任务 |
| GET | `/api/tasks/{id}` | `task:read` | 查询任务详情 |
| POST | `/api/tasks/{id}/submit` | `task:submit` | 提交任务 |
| POST | `/api/tasks/{id}/accept` | `task:accept` | 主负责人接受任务 |
| POST | `/api/tasks/{id}/submit-review` | `task:review` | 提交审核 |
| POST | `/api/tasks/{id}/approve` / `reject` | `task:approve` | 审核任务 |
| POST | `/api/tasks/{id}/start` | `task:submit` | 驳回后重新开始 |
| POST | `/api/tasks/{id}/cancel` / `archive` | 对应权限 | 取消或归档 |
| POST | `/api/tasks/{id}/transfer` | `task:assign` | 替换主负责人和协作人 |

## 并发与权限

所有命令请求都携带任务 `version`。服务层通过 MyBatis-Plus 乐观锁拦截器更新任务，更新失败返回稳定的 `COMMON_409`。任务查询先根据角色数据范围解析可见任务 ID，再叠加状态和项目条件；Controller 的权限注解不能替代服务层的资源归属检查。
