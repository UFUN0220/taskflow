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

首次使用可以复制 `.env.example` 为 `.env` 后执行：

```powershell
docker compose up -d
docker compose ps
```

旧的基础设施启动命令仍可用于只启动中间件：`docker compose up -d mysql redis rabbitmq minio`。完整应用启动请使用上面的初始化脚本。

## Maven 用户目录

Maven 用户配置已迁移到 `F:\projects_2027\taskflow-platform\maven-user`，其中：

- `settings.xml` 指定 F 盘本地仓库；
- `wrapper` 保存 Maven Wrapper 发行版；
- `repository` 保存项目依赖缓存。

当前用户环境变量已设置为：`MAVEN_USER_HOME` 指向该目录，`MAVEN_ARGS` 指向 `settings.xml`，`JAVA_HOME` 指向 `F:\JDK17`。环境变量对新启动的终端生效，旧终端需要重新打开。

## Flyway 数据库初始化

启动 MySQL 后，通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置连接，并设置 `FLYWAY_ENABLED=true`。应用启动时会执行 `classpath:db/migration` 下的版本迁移。阶段 6 的 V6 增加草稿编辑和删除权限；如需本地管理员，再设置 `TASKFLOW_BOOTSTRAP_ADMIN_ENABLED=true` 和 `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD`，密码不会写入迁移脚本。

## 应用

后端默认监听 8080，前端开发服务器监听 5173。使用 `npm run dev` 时，Vite 将 `/api` 和 `/ws` 代理到宿主机后端；使用 Compose 时，前端由 Nginx 提供静态文件和同源代理。

如果需要本地数据库连接，复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml` 并修改凭据；该文件已被 Git 忽略。
