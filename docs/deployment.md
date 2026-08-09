# 阶段 16 部署说明

## 完整容器模式

阶段 16 的 Compose 会启动前端、后端、MySQL、Redis、RabbitMQ 和 MinIO 六个服务。后端容器通过内部服务名连接 `mysql:3306`、`redis:6379`、`rabbitmq:5672` 和 `minio:9000`；浏览器只访问前端 Nginx，Nginx 将 `/api`、`/actuator` 和 `/ws` 代理到 `backend:8080`。

首次使用执行：

```powershell
.\scripts\init-compose.ps1 -Rebuild
```

脚本会在 `.env` 不存在时从 `.env.example` 创建，并等待所有服务健康。默认前端地址为 `http://localhost:5173`，后端健康检查为 `http://localhost:8080/actuator/health`。端口和凭据均可通过 `.env` 覆盖。

停止服务但保留卷：

```powershell
.\scripts\cleanup-compose.ps1
```

删除卷会清空本地数据库、Redis、RabbitMQ 和 MinIO 数据，必须显式确认：

```powershell
.\scripts\cleanup-compose.ps1 -RemoveVolumes -ConfirmDataLoss
```

## 本地基础设施

`docker-compose.yml` 仅启动开发所需的 MySQL、Redis、RabbitMQ 和 MinIO，并为每个服务配置健康检查和命名卷。宿主机端口使用需求约定的非默认映射：3307、6380、5673、15673、9000 和 9001。

首次使用可以复制 `.env.example` 为 `.env`，然后填写其中所有必填的本地开发 Secret 后执行：

```powershell
docker compose up -d
docker compose ps
```

`.env.example` 现在只保留空值和安全说明，不包含可直接复用的数据库、RabbitMQ、MinIO 或 JWT 凭据。未填写时 `docker compose config --quiet`/`up` 会按预期 fail-fast；阶段 1 已用仅存在于当前进程的非敏感测试值验证 Compose 模板可以解析。不要把 `.env`、证书私钥或运行时 Secret 提交到 Git。

旧的基础设施启动命令仍可用于只启动中间件：`docker compose up -d mysql redis rabbitmq minio`。完整应用启动请使用上面的初始化脚本。

## Maven 用户目录

Maven 用户配置已迁移到 `F:\newinstall\maven-user`，本地仓库位于 `F:\newinstall\maven-repository`，其中：

- `settings.xml` 指定 F 盘本地仓库；
- `wrapper` 保存 Maven Wrapper 发行版；
- `repository` 保存项目依赖缓存。

当前用户环境变量已设置为：`MAVEN_USER_HOME` 指向该目录，`MAVEN_ARGS` 指向 `settings.xml`，`JAVA_HOME` 指向 `F:\JDK17`。环境变量对新启动的终端生效，旧终端需要重新打开。

## 工具缓存位置

本项目不在 C 盘保存非必要的项目工具缓存：

- Gradle：`F:\newinstall\gradle-user-home`；Gradle 安装目录位于 `F:\newinstall`；
- npm：`F:\newinstall\npm-cache`；
- Testcontainers：项目内 `src/test/resources/testcontainers.properties`；
- Maven：提交到仓库的 `.mvn/maven.config` 使用跨平台项目相对目录 `.m2-local/repository`；Windows 本地会话可通过 `MAVEN_USER_HOME`/`MAVEN_ARGS` 指向 `F:\newinstall\maven-user` 和 `F:\newinstall\maven-repository`，GitHub Actions 会清空这些 Windows 专用参数。

Docker Desktop 的运行时和必要用户配置由 Docker 管理，未对 Docker 数据目录或卷做迁移、删除操作。

## Flyway 数据库初始化

启动 MySQL 后，通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置连接，并设置 `FLYWAY_ENABLED=true`。应用启动时会执行 `classpath:db/migration` 下的版本迁移。阶段 6 的 V6 增加草稿编辑和删除权限；如需本地管理员，再设置 `TASKFLOW_BOOTSTRAP_ADMIN_ENABLED=true` 和 `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD`，密码不会写入迁移脚本。生产必须使用 `SPRING_PROFILES_ACTIVE=prod`，由外部注入所有必需 Secret；生产 profile 不接受开发默认值。

## 应用

后端默认监听 8080，前端开发服务器监听 5173。使用 `npm run dev` 时，Vite 将 `/api` 和 `/ws` 代理到宿主机后端；使用 Compose 时，前端由 Nginx 提供静态文件和同源代理。

如果需要本地数据库连接，复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml` 并修改凭据；该文件已被 Git 忽略。

生产管理面边界：prod 默认只开放 Actuator 健康探针，Swagger/OpenAPI 默认关闭；前端 Nginx 仅代理 `/actuator/health`。生产 HTTPS/WSS 应在可信反向代理或 Ingress 终止，并明确清洗和传递 `X-Forwarded-*`，应用默认不信任这些 Header。

## 2026-08-09 全面验收记录

本次验收使用现有 Compose 编排启动六个服务，最终 backend、frontend、mysql、redis、rabbitmq、minio 均为 `healthy`。后端健康接口、前端入口、管理员登录、当前用户、任务列表以及未授权/非法 Token 拒绝均通过；Flyway 最新版本为 V8。

随后执行了保留数据卷的 `docker compose restart`。重启前后 Flyway 版本保持 V8，后端恢复健康并可以再次登录。该结果只证明当前本地 Compose 拓扑的重启恢复，不代表 Kubernetes 实机恢复或生产级高可用。

认证遗留修复后，后端镜像增加了 Redis 活动会话、后端登出撤销和登录失败窗口限流，并已重新完成 Docker 编译；真实“登出后旧 Token 返回 401”烟测和 10 次/60 秒限流烟测均已通过。
