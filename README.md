# TaskFlow Platform

## Stage 9 progress

The backend now includes task draft maintenance, filtered and paginated task queries, primary/collaborator assignment, scoped detail access, batch assignee loading, task operation logs, a fixed task state machine, optimistic concurrency control using old-status-plus-version conditional updates, task comments, MinIO-backed attachment metadata workflows, persistent reminder plans, Redis ZSet scheduling, distributed scanning locks, and RabbitMQ reminder publishing. Flyway V1 through V7 are designed for a fresh MySQL database.

企业任务协同与流程管理平台，面向学习和校招面试准备，采用模块化单体架构逐阶段实现。

## 当前进度

当前阶段 9 已完成提醒计划持久化、截止时间/任务终态联动、Redis ZSet 近期待触发索引、Redis 数据恢复、分布式锁和 RabbitMQ 到期消息发布；RabbitMQ 消费者和站内通知仍属于阶段10。

## 技术栈

- 后端：Java 17、Spring Boot 3.4.x、Spring Security、Maven Wrapper；
- 前端：React、TypeScript、Vite、React Router、Axios、Ant Design；
- 基础设施：MySQL 8、Redis 7、RabbitMQ 3、MinIO；
- 运行方式：Windows 11、Docker Desktop、Docker Compose。

## 目录

```text
backend/
├─ src/main/java/yvon/backend/       # Spring Boot 后端
├─ src/test/                         # 后端测试
├─ frontend/                         # React + Vite 前端
├─ docker-compose.yml                 # 阶段 0 基础设施
├─ .env.example                       # 本地环境变量模板
└─ docs/                              # 持续维护的项目文档
```

## 启动基础设施

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

端口映射：MySQL `3307`、Redis `6380`、RabbitMQ `5673`、RabbitMQ 管理台 `15673`、MinIO API `9000`、MinIO Console `9001`。

## 启动后端

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Maven 用户目录已迁移到 `F:\projects_2027\taskflow-platform\maven-user`，包含 `settings.xml`、Wrapper 发行版和本地仓库。新的 PowerShell 或 IDE 会话会通过用户环境变量 `MAVEN_USER_HOME`、`MAVEN_ARGS` 和 `JAVA_HOME` 自动使用该目录；迁移后请重新打开终端或 IDE。

健康检查：<http://localhost:8080/api/health> 或 <http://localhost:8080/actuator/health>。

## 执行数据库迁移

先启动 MySQL 并准备本地配置，再显式开启 Flyway：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3307/taskflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "taskflow"
$env:DB_PASSWORD = "change-me-in-local-config"
$env:FLYWAY_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

迁移脚本位于 `src/main/resources/db/migration/`，阶段8新增 `V7__seed_comment_attachment_permissions.sql`；阶段9复用 V1 的 `reminder_plan` 表，不新增迁移。附件和提醒默认关闭；启用提醒时需配置 Redis、RabbitMQ。无数据库时默认关闭 Flyway，便于运行基础测试。

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

前端地址：<http://localhost:5173>。`/api` 请求在开发服务器中代理到后端 `8080` 端口。

## 验证命令

```powershell
.\mvnw.cmd test
Set-Location frontend; npm run build
Set-Location ..; docker compose config --quiet
```

## 文档

- [阶段 0 部署说明](docs/deployment.md)
- [需求与角色](docs/requirements.md)
- [数据库设计](docs/database.md)
- [安全与数据范围](docs/security.md)
- [阶段 4 角色与组织管理](docs/stage4-management.md)
- [阶段 5 项目与任务管理](docs/stage5-project-task.md)
- [阶段 6 任务查询与分配](docs/stage6-task-query.md)
- [阶段 7 状态机与并发控制](docs/stage7-concurrency.md)
- [阶段 8 评论与附件](docs/stage8-comments-attachments.md)
- [阶段 9 提醒计划](docs/stage9-reminders.md)
- [任务状态机](docs/task-state-machine.md)
- [消息与提醒边界](docs/message-flow.md)
- [测试说明](docs/testing.md)
- [架构说明](docs/architecture.md)
- [阶段学习笔记](docs/learning-notes.md)
- [面试笔记](docs/interview-notes.md)
