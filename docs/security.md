# 阶段 1 安全与权限边界

## 阶段 1 配置与运行边界

配置按 profile 分层：`dev` 保留本地开发便利值，`test` 使用测试专用值，`prod` 不提供敏感配置默认值。生产启动会校验 JWT、MySQL、RabbitMQ、MinIO、bootstrap admin（启用时）和 WebSocket 来源配置；缺失、过短或包含 `change-me`、`default`、`password`、`local` 等弱标记的值会 fail-fast。`.env.example`、`k8s/secret.yaml` 只提供变量名和空值模板，真实值必须通过本机未提交的 `.env`、部署平台 Secret 或其他外部注入方式提供。

当前已验证的边界：

- Spring Security 增加 `CSP`、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy` 和生产 HSTS；CSP 可通过 `TASKFLOW_SECURITY_HEADERS_CSP` 调整；
- prod 仅配置 Actuator `health`，关闭 Swagger/OpenAPI UI 与 JSON；前端 Nginx 只代理精确的 `/actuator/health`，其他 Actuator 路径返回 404；
- 应用默认 `server.forward-headers-strategy=none`，不信任任意 `X-Forwarded-For`/`X-Forwarded-Proto`。若部署在可信反向代理后，必须把代理网络边界、Header 清洗和 HTTPS/WSS 终止策略一并配置和验证；
- CSRF 目前保持关闭，因为 REST 和 STOMP 都使用显式 Bearer Token。若以后迁移 HttpOnly Cookie，必须同步启用并测试 CSRF、SameSite、Secure、登出和 STOMP 鉴权，不能只改前端存储位置；
- 前端仍使用 `localStorage` 保存 Bearer Token。本阶段没有做半成品 Cookie 迁移，CSP 只降低脚本注入面，不能消除 XSS 读取 Token 的风险。

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
