# 阶段 4 性能与可观测性对比（2026-08）

## 结论

本阶段完成了可控的前端首屏拆分、SQL/索引复核和运行时指标扩展。前端路由懒加载已使入口相关最大共享 JS 从旧构建的 1,156,965 bytes 降至新构建最大共享 chunk 的 747,991 bytes，下降约 35.38%。这不是总传输量下降的证明：Ant Design 表格等依赖被拆成独立 chunk，必须结合真实首屏网络瀑布继续评估。

同一组阶段 15 参数的 20 并发压测未能在本阶段重新执行。现有可用账号不是原基线使用的管理员数据范围，使用它会改变任务可见集合和写权限路径。因此没有把不可比的结果伪装成“优化后 QPS/p95/p99”。机器可读状态见 `performance-baseline-after-optimization.json`。

## 前端构建对比

| 项目 | 优化前 | 优化后 | 变化 |
| --- | ---: | ---: | ---: |
| 旧入口 JS | 1,156,965 bytes | — | — |
| 最大共享 JS chunk | — | 747,991 bytes | 相对旧入口下降 408,974 bytes / 35.38% |
| 路由 chunk | 无明显页面级拆分 | Task 96,457；Management 6,546；Dashboard 3,354；Login 2,677 bytes | 已按路由拆分 |
| Vite 500 KB 提示 | 触发 | 仍触发（最大共享 chunk 约 748 KB） | 需进一步分析 vendor 包，当前不计性能分加分 |

本次 `npm run build` 同时通过 TypeScript typecheck 和 Vite production build。构建产物大小来自 `frontend/dist/assets`，不是压缩网络传输抓包；未声称总 JS 体积下降。

## SQL 与索引

对任务列表和通知列表执行了只读 EXPLAIN，并核对了现有索引。当前小规模本地数据中，两条采样查询均选择 `PRIMARY` 反向扫描并过滤，虽然相关复合索引存在，但该计划在当前 `LIMIT` 和数据分布下并不自动证明索引缺失。

代码审查确认任务列表的负责人数据采用批量 `IN` 查询，通知列表为单次分页查询，未发现需要立即修复的 N+1。阶段 4 没有新增索引和 Flyway migration；后续应在更大数据量、更多筛选组合和深分页场景下重新比较。

完整证据见 `performance-explain-after-optimization.txt`。

## 运行时观测

`performance-runtime-after-optimization.json` 在健康的本地 Compose 后端上采集，使用受保护 Actuator 端点和 Redis 容器 INFO，不包含密码或令牌。关键快照：

- `http.server.requests`：222 次，累计 1.160416585 秒，最大 0.07479974 秒；这是采集窗口内的累计快照，不是 20 并发压测结果。
- HikariCP：active=0、idle=10、pending=0、max=10；usage count=100，累计 0.194 秒，max=0.008 秒。
- Spring executor：active=0、queued=3。
- RabbitMQ：connections=1、channels=1；listener 指标在当前 Actuator 暴露范围返回 404，published/consumed 当前计数为 0，不能解释为系统永远没有消息。
- JVM：GC pause count=2、累计 0.009 秒；memory.used=268,383,064 bytes。
- Redis INFO：instantaneous_ops_per_sec=9、rejected_connections=0、evicted_keys=0、keyspace_hits=21,171、keyspace_misses=8,280；这是容器启动以来的累计/瞬时快照，不是命中率基准。

## 同参数复测状态

阶段 15 原基线参数为 10 个部门、100 个用户、1000 个预置任务、20 并发、预热 10 秒、采样 60 秒、相同随机种子。管理员凭据不可用时，本阶段拒绝使用员工账号替代，以避免授权范围、任务集合和写入结果不一致。

因此：

- 没有新的 QPS、平均响应时间、p95、p99 或错误率可用于前后性能结论；
- 原基线仍是唯一完成的六场景压测证据；
- 前端拆分和运行时采集可以计入工程改动证据，但本阶段不因未完成可比压测而上调性能/评估评分；
- 重新提供本地管理员凭据后，应按 `performance-baseline-after-optimization.json` 中的参数执行压测，并补充同机、同数据规模的差异表。

## 阶段判定

代码与静态证据：完成。前端构建：通过。SQL/索引调整：无证据支持，保持不变。运行时指标：已采集。可比压测：阻塞，不能包装为优化成功或生产容量结论。
