# 阶段 5：故障注入与恢复验收（2026-08-09）

## 验收边界

本阶段只验证当前本地 Docker Compose 单实例拓扑中的短暂停止/启动恢复，不将容器重启包装为生产 HA、故障切换或数据服务 SLA。所有演练均保留 Docker 卷，没有执行 `docker compose down`、`docker rm`、`docker volume rm`、数据库清空或 Flyway 回退。

可重复脚本见 [`scripts/fault-injection.ps1`](../scripts/fault-injection.ps1)。脚本默认计划模式；只有显式传入 `-Execute` 才会停止并启动精确的 `taskflow-platform-*` 容器。认证 Token 和数据库密码只通过当前进程环境变量提供，不写入结果文件。

## 证据矩阵

| 场景 | 注入方式 | 实际结果 | 数据一致性 | 判定 |
| --- | --- | --- | --- | --- |
| Redis 短暂不可用 | 停止/启动 `taskflow-platform-redis-1` | 期间 Redis PING、提醒 ZSet 和 `/api/auth/me` 不可用；恢复后 Redis PONG、ZSet 和 `/api/auth/me` HTTP 200 | Flyway V8、任务 3628、通知 2000、附件 0，前后不变 | **通过当前范围** |
| RabbitMQ 短暂不可用 | 停止/启动 `taskflow-platform-rabbitmq-1` | 期间 Rabbit 队列查询不可用，但后端健康接口仍 HTTP 200；恢复后 3 个业务队列重新可查询且 ready/unacked 均为 0 | Flyway V8、任务 3628、通知 2000、附件 0，前后不变 | **通过容器恢复范围** |
| RabbitMQ 消费失败、重试、DLQ | 计划向业务交换机注入唯一非法事件 | 当前执行审批拒绝了消息注入命令，未声称真实 Rabbit retry/DLQ 已通过 | 已有 `NotificationMessageConsumerTest` 和代码证据；缺少本轮真实 broker 消息证据 | **未完成** |
| MinIO 短暂不可用 | 停止 MinIO；内存构造小型文本附件并上传到现有可见任务 2 | 上传 HTTP 500；任务 2 附件元数据状态为 `FAILED=1`；恢复后 MinIO live HTTP 200 | 没有 AVAILABLE 脏元数据；对象列表未直接采集，不能宣称已完成孤儿对象扫描 | **通过失败状态补偿范围** |
| MySQL 保卷重启 | 计划停止/启动 MySQL | 本阶段命令被当前执行审批拒绝，未执行 | 既有部署验收记录保留卷重启后 Flyway V8 和后端恢复；不是本阶段新证据 | **未完成本阶段复验** |
| 后端重启 / WebSocket | 计划重启 backend 并观察浏览器重连 | 本阶段重启命令被当前执行审批拒绝；未生成浏览器重连和真实 MESSAGE 证据 | 现有 Stage 3 浏览器结果为 STOMP CONNECTED 后订阅失败，不能升级为重连通过 | **未完成** |

## Redis 演练

前置条件：六服务 Compose 已健康，使用现有本地测试账号取得短期访问令牌；数据库快照通过只读 MySQL 查询采集。

注入：`.scriptsault-injection.ps1 -Scenario redis -Execute -Output docsault-injection-redis-2026-08-09.json`。

实际观察：

- 注入前 Redis `PING=PONG`、提醒 ZSet cardinality=0、`/api/auth/me=200`；
- Redis 停止期间 Redis 快照为 unavailable，`/api/auth/me` 无 HTTP 响应；
- Redis 启动并健康后，`PING=PONG`、ZSet 可读、`/api/auth/me=200`；
- Flyway=8、tasks=3628、notifications=2000、attachments=0 前后不变。

当前数据没有可到期的提醒计划，因此本次没有观察到真实提醒发布；Redis 丢失后的数据库重建由 `ReminderRedisIndexServiceTest` 覆盖，真实重建调度周期仍未等待 5 分钟验证。

## RabbitMQ 演练

注入：`.scriptsault-injection.ps1 -Scenario rabbitmq -Execute -Output docsault-injection-rabbitmq-2026-08-09.json`。

实际观察：RabbitMQ 停止期间后端 `/api/health` 仍返回 200，说明健康端点和 MySQL 核心事实不直接依赖消息代理；RabbitMQ 队列查询不可用，恢复后 `taskflow.reminder.dead`、`taskflow.reminder.dispatch`、`taskflow.reminder.retry` 均恢复为 ready=0、unacked=0。任务、通知和附件计数未变化。

本轮未完成真实非法消息注入，因此以下结论仍只来自代码和自动化测试：消费者使用手动 Ack、最大 3 次尝试、5 秒 TTL 重试队列和死信队列；通知通过 `source_message_id + user_id` 唯一约束幂等。应在执行审批允许后补一条唯一 `FAULT_STAGE5_*` 消息，记录 retry queue、dead queue 和 `notification_dead_letter` 行数变化，再验证 replay 不重复生成通知。

## MinIO 演练

注入：停止 MinIO 后，通过现有访问令牌上传一份内存中的 `text/plain` 小文件到任务 2，再启动 MinIO。上传返回 HTTP 500；只读查询显示该任务附件状态为 `FAILED=1`。MinIO 恢复后 live health 返回 HTTP 200，数据库快照中的 Flyway、任务、通知计数不变。

这证明了“对象写入失败 → 元数据进入 FAILED”的当前补偿边界。没有直接列举 MinIO 对象目录，故不把“无孤儿对象”写成已验证结论；对象成功而元数据状态更新失败的反向补偿仍主要由 `TaskAttachmentServiceTest` 的 Mock 测试覆盖。

## MySQL、应用和 WebSocket

本阶段 MySQL 停止和 backend 重启被执行审批拒绝，未绕过限制。既有 `docs/deployment.md` 记录过保留卷 Compose 重启后 Flyway V8、后端健康和登录恢复；这属于既有本地证据，不冒充本阶段新演练。

WebSocket 也没有新的浏览器证据。Stage 3 已观察到浏览器真实建立 `/ws/notifications` 并收到 STOMP `CONNECTED`，但订阅随后失败；因此本阶段不声称后端重启后的浏览器自动重连或离线通知补拉通过。当前设计仍以 MySQL `notification` 为最终事实，WebSocket 只负责实时体验，客户端通过 HTTP 未读接口补拉。

## 结果文件

- Redis：`docs/fault-injection-redis-2026-08-09.json`
- RabbitMQ：`docs/fault-injection-rabbitmq-2026-08-09.json`
- MinIO：`docs/fault-injection-minio-2026-08-09.json`
- 计划模式安全检查：`docs/fault-injection-dry-run.json`

## 阶段判定

Redis 和 MinIO 的本地短故障恢复及核心数据库不变性已有真实证据；RabbitMQ 容器恢复已有真实证据，但真实消费失败 → 有限重试 → DLQ → replay 尚未完成；MySQL 保卷重启、后端重启后的浏览器 WebSocket 重连和离线补拉本阶段未复验。项目仍不是生产 HA 结论。
