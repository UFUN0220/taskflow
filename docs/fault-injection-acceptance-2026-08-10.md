# TaskFlow Platform 阶段 12 故障注入自动化与恢复闭环

日期：2026-08-10

## 当前判定

本地和 GitHub Actions 均已真实执行 Stage12 Testcontainers 演练并通过。远程 run `31385404134` 的 job `93444588147` checkout 了包含 `4681da9e94352e09fb3337a484ec556e5533d3fd` 的 PR merge ref；Maven `verify` 输出为 84 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。Stage12 因此升级为远程运行验证。

当前状态：`RUNTIME_VERIFIED_REMOTE`。

## 基线冻结

- `git diff --check`：通过；
- 阶段 11.6：backend direct 9/9，Nginx proxy 连续 3×9/9；
- 阶段 11.6 后端单测基线：79 tests，0 failures，1 skipped；
- 阶段 11.6 Testcontainers integration verify 基线：BUILD SUCCESS，0 failures，0 skipped；
- Flyway：V8；
- 既有 Compose 故障脚本仍只代表本地单实例 stop/start 证据，不替代本阶段 Testcontainers 证据。

## 本阶段实现

### Redis 认证 fail-closed

`AuthSessionService.isActive()` 在 Redis 查询发生 `DataAccessException` 时返回 `false`，不会把 Redis 不可用转化为已认证请求。新增单元测试验证连接异常时会话认证被拒绝。

### Testcontainers 可靠性测试

新增 `Stage12ReliabilityContainerTest`，仅在以下命令中执行：

```powershell
.\mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage12ReliabilityContainerTest" test
```

测试使用隔离的 MySQL、RabbitMQ、Redis、MinIO 容器，不接触开发者现有 Compose 容器和数据库卷。

实际断言：

- Rabbit：真实消息、x-taskflow-retry-count=1/2、DLQ、真实 `NotificationDeadLetterService.replay()`、同一 message id 重复投递只产生一条 notification；
- Redis：在真实 Redis 容器中执行 `FLUSHALL` 模拟派生状态丢失，再由 `ReminderRedisIndexService.rebuildFromDatabase()` 恢复；
- MinIO：真实容器对象成功写入，使用短超时隔离错误端点验证不可用时 FAILED 路径，再用真实容器验证数据库更新异常后的对象删除补偿；
- MySQL：同一 Testcontainers 容器 ID 原生 restart，使用容器内 SQL 验证 Flyway 仍为 V8、事实快照和明确业务 ID 不变。

本地命令与结果：

```powershell
.\mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage12ReliabilityContainerTest" test
# Tests run: 4, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
```

远程命令与结果：

```bash
bash ./mvnw -Dtaskflow.integration=true verify
# Tests run: 84, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
```

远程 Surefire artifact 因后续 OWASP 步骤被 runner shutdown 而未上传；下面的 testcase 名称来自当前源码及本地 Surefire 结果，远程执行由同一 Maven integration 开关确认：

| Test method | Infrastructure | Reliability capability | Result |
|---|---|---|---|
| `rabbitRetryDlqReplayAndDuplicateDeliveryAreObservable` | RabbitMQ + MySQL | retry count 1/2、maxAttempts=3 到 DLQ、真实 replay、同一 message id 重复投递只保留一条 notification 事实 | PASS_LOCAL + PASS_REMOTE |
| `redisLossFailsClosedAndReminderIndexRebuildsFromDatabaseFixture` | Redis + MySQL | Redis 认证查询异常 fail-closed；真实 Redis `FLUSHALL` 后保留 MySQL reminder 事实并 rebuild ZSet 派生索引 | PASS_LOCAL + PASS_REMOTE |
| `minioSuccessUnavailableAndOrphanCompensationAreObservable` | MinIO + MySQL | 真实对象成功写入；隔离错误端点触发不可用 FAILED 语义；metadata 异常时删除已写对象 | PASS_LOCAL + PASS_REMOTE |
| `mysqlRestartKeepsFlywayAndCoreFactsOnTheSameContainerStorage` | MySQL | 同一 Testcontainers 容器 restart 后等待 SQL 恢复，Flyway 仍为 V8，核心事实快照/业务 ID 不变 | PASS_LOCAL + PASS_REMOTE |

测试方法与能力不能按数量简单等同：第一个方法覆盖 RabbitMQ 的四个断言族，第二、第三个方法分别覆盖派生状态恢复和对象补偿边界。

## 统一可靠性矩阵

| Scenario | Failure | Expected | Actual | Automated | Status |
|---|---|---|---|---|---|
| Rabbit retry | 确定性消费者失败 | retry count 按 1、2 增长 | 真实 broker 消费通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| Rabbit DLQ | 达到 maxAttempts=3 | 原 message id 进入 DLQ | 真实 DLQ 消费通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| Rabbit replay | 调用真实 replay service | 回到主队列并成功消费 | 真实 replay 通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| Rabbit duplicate delivery | 同一 message id 两次 | notification 唯一事实为 1 | 唯一事实计数为 1 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| Redis derived-state loss | 真实 Redis `FLUSHALL` | ZSet 丢失后可重建 | 真实容器 + service rebuild 通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| MinIO successful upload | 真实 MinIO 对象写入 | 对象可 stat，大小一致 | 真实对象 stat 通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| MinIO unavailable upload | 隔离错误端点、2 秒客户端超时 | 上传失败并调用 FAILED 状态路径 | 业务失败语义和 `markFailed` 通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| MinIO orphan compensation | metadata 更新异常 | 已写对象被删除 | 真实对象 stat 不存在 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| MySQL restart | 同一容器原生 restart | Flyway V8、事实计数和 ID 不变 | 容器内 SQL 快照一致 | 远程 84-test verify 包含该类 | PASS_REMOTE |
| Datasource recovery | MySQL 恢复后 SQL 查询 | SELECT 1 与事实查询成功 | 容器内查询通过 | 远程 84-test verify 包含该类 | PASS_REMOTE |
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

## CI 与依赖扫描结论

- `fast-check` run `31385404096`：PASS，backend tests/frontend build/static manifests 通过。
- `integration-security` run `31385404134`：Maven unit + Testcontainers + JaCoCo PASS，npm ci PASS，npm audit PASS（remote log: found 0 vulnerabilities）。
- OWASP Dependency-Check：不是漏洞门禁结论。该步骤在未提供 `NVD_API_KEY` 时开始下载 NVD 374,572 条记录，达到 180,000/374,572（48%）时 runner 收到 shutdown signal，退出码 143；后续 gate 与 artifact upload 被跳过，job conclusion 为 `cancelled`。因此分类为 `SCAN_INFRA_FAILURE / RUN_CANCELLED`，没有新的 CVE 报告，也不能写成“扫描通过”或“漏洞为 0”。
- `integration-security` 整体不能记为 PASS；Stage12 运行证据与依赖扫描结论保持独立。

后续依赖扫描仍需在有 NVD API key 或预热缓存的 CI 环境重新执行：

远程检查仍需重新执行并保存真实结果：

```powershell
.\mvnw.cmd test
.\mvnw.cmd "-Dtaskflow.integration=true" verify
```

并补充 frontend typecheck/build、Compose/Kustomize 静态验证，以及至少一次阶段 11.6 Nginx proxy 9/9 smoke。在新的安全扫描运行完成前，依赖安全门禁保持未闭环；这不回退 Stage12 的 `RUNTIME_VERIFIED_REMOTE` 状态。
