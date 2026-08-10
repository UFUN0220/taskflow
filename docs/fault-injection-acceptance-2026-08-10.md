# TaskFlow Platform 阶段 12 故障注入自动化与恢复闭环

日期：2026-08-10

## 当前判定

本阶段的测试代码已经实现并通过 Maven test-compile，但本轮执行器拒绝启动高权限 Testcontainers 测试，普通沙箱也拒绝启动 PowerShell。因此本文件不把以下场景标记为自动化通过：

```text
Rabbit retry → DLQ → replay
Redis loss → rebuild
MinIO failure → recovery/orphan compensation
MySQL same-container restart persistence
```

当前状态：`IMPLEMENTED_NOT_RUNTIME_VERIFIED`。

执行故障：`mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage12ReliabilityContainerTest" test` 被执行权限/执行器拒绝，未获得测试报告，不能伪造结果。

## 基线冻结

- `git diff --check`：通过；
- 阶段 11.6：backend direct 9/9，Nginx proxy 连续 3×9/9；
- 阶段 11.6 后端单测基线：79 tests，0 failures，1 skipped；
- 阶段 11.6 Testcontainers integration verify 基线：BUILD SUCCESS，0 failures，0 skipped；
- Flyway：V8；
- 既有 Compose 故障脚本仍只代表人工 Docker stop/start 证据，不替代本阶段 Testcontainers 证据。

## 本阶段实现

### Redis 认证 fail-closed

`AuthSessionService.isActive()` 在 Redis 查询发生 `DataAccessException` 时返回 `false`，不会把 Redis 不可用转化为已认证请求。新增单元测试验证连接异常时会话认证被拒绝。

### Testcontainers 可靠性测试

新增 `Stage12ReliabilityContainerTest`，仅在以下命令中执行：

```powershell
.\mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage12ReliabilityContainerTest" test
```

测试使用隔离的 MySQL、RabbitMQ、Redis、MinIO 容器，不接触开发者现有 Compose 容器和数据库卷。

计划断言：

- Rabbit：真实消息、x-taskflow-retry-count=1/2、DLQ、真实 `NotificationDeadLetterService.replay()`、同一 message id 重复投递只产生一条 notification；
- Redis：真实 ZSet 丢失后由 `ReminderRedisIndexService.rebuildFromDatabase()` 恢复；
- MinIO：真实对象成功、真实存储不可用时 FAILED 路径、数据库更新异常后的真实对象删除补偿；
- MySQL：同一 Testcontainers 容器 ID 停止/启动，Flyway 仍为 V8，事实快照和明确业务 ID 不变。

## 统一可靠性矩阵

| Scenario | Failure | Expected | Actual | Automated | Status |
|---|---|---|---|---|---|
| Rabbit retry | 确定性消费者失败 | retry count 按 1、2 增长 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| Rabbit DLQ | 达到 maxAttempts=3 | 原 message id 进入 DLQ | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| Rabbit replay | 调用真实 replay service | 回到主队列并成功消费 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| Rabbit duplicate delivery | 同一 message id 两次 | notification 唯一事实为 1 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| Redis auth unavailable | Redis 查询失败 | fail-closed | 单元测试待完整回归 | 已实现 | PENDING_TEST |
| Redis reminder rebuild | ZSet 丢失 | 从数据库 fixture 重建 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| MinIO successful upload | 正常对象写入 | 对象可 stat，大小一致 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| MinIO unavailable upload | 对象存储停止 | 上传失败并调用 FAILED 状态路径 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| MinIO orphan compensation | metadata 更新异常 | 已写对象被删除 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| MySQL restart | 同一容器停止/启动 | Flyway V8、事实计数和 ID 不变 | 未运行 | 已实现 | NOT_RUNTIME_VERIFIED |
| Datasource recovery | MySQL 恢复后 JDBC 查询 | SELECT 1 与事实查询成功 | 未运行 | 部分实现 | NOT_RUNTIME_VERIFIED |
| Backend restart | backend 实例重启 | consumer/scheduler/REST 恢复 | 未实现本轮自动化 | 否 | OPEN |
| Reminder recovery | backend restart 后 reminder 恢复 | 未实现本轮自动化 | 否 | OPEN |
| Notification REST recovery | WebSocket 断开后 REST 补拉 | 保留 MySQL notification 事实 | 已有设计，未本轮重跑 | 部分 | OPEN |

## 证据边界

即使上述 Testcontainers 测试全部通过，也只能说明：

`LOCAL_SINGLE_INSTANCE`

不能说明：

- RabbitMQ Cluster 或 exactly-once；
- Redis Cluster 或 HA failover；
- MySQL 主从、云数据库或跨 AZ 恢复；
- MinIO distributed mode；
- backend 多副本无损切换；
- 生产 SLA 或 zero downtime。

## 后续运行门禁

获得可用执行权限后，必须重新执行并保存真实结果：

```powershell
.\mvnw.cmd test
.\mvnw.cmd "-Dtaskflow.integration=true" verify
```

并补充 frontend typecheck/build、Compose/Kustomize 静态验证，以及至少一次阶段 11.6 Nginx proxy 9/9 smoke。远程 GitHub Actions 在没有真实运行记录前标记为 `REMOTE_NOT_VERIFIED`。
