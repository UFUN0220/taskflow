# 阶段 0 部署说明

## 本地基础设施

`docker-compose.yml` 仅启动开发所需的 MySQL、Redis、RabbitMQ 和 MinIO，并为每个服务配置健康检查和命名卷。宿主机端口使用需求约定的非默认映射：3307、6380、5673、15673、9000 和 9001。

首次使用可以复制 `.env.example` 为 `.env` 后执行：

```powershell
docker compose up -d
docker compose ps
```

阶段 0 不提供删除卷的清理命令，避免误删本地数据。后续如需清理，必须明确确认目标卷和数据范围。

## Maven 用户目录

Maven 用户配置已迁移到 `F:\projects_2027\taskflow-platform\maven-user`，其中：

- `settings.xml` 指定 F 盘本地仓库；
- `wrapper` 保存 Maven Wrapper 发行版；
- `repository` 保存项目依赖缓存。

当前用户环境变量已设置为：`MAVEN_USER_HOME` 指向该目录，`MAVEN_ARGS` 指向 `settings.xml`，`JAVA_HOME` 指向 `F:\JDK17`。环境变量对新启动的终端生效，旧终端需要重新打开。

## Flyway 数据库初始化

启动 MySQL 后，通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置连接，并设置 `FLYWAY_ENABLED=true`。应用启动时会执行 `classpath:db/migration` 下的版本迁移。阶段 6 的 V6 增加草稿编辑和删除权限；如需本地管理员，再设置 `TASKFLOW_BOOTSTRAP_ADMIN_ENABLED=true` 和 `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD`，密码不会写入迁移脚本。

## 应用

后端默认监听 8080，前端开发服务器监听 5173。阶段 0 的 Compose 文件不启动应用容器；完整应用容器化属于阶段 16。

如果需要本地数据库连接，复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml` 并修改凭据；该文件已被 Git 忽略。
