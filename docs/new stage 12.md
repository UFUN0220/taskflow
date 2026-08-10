继续 TaskFlow Platform 调优。

当前阶段 11.6 已完成，本地 Docker Compose Nginx WebSocket Proxy P1 已关闭。

当前已验证：

- Backend direct Playwright：9/9
- Nginx proxy Playwright：
  - Run 1：9/9
  - Run 2：9/9
  - Run 3：9/9
- 浏览器真实 STOMP 链路：
  CONNECT
  → CONNECTED
  → SUBSCRIBE
  → `/app/notifications/ready`
  → `SUBSCRIPTION_READY MESSAGE`
  → 真实业务事件
  → 业务 `MESSAGE`
  → UI 更新
- Playwright worker 重启产生 409 的 fixture 问题已关闭
- Maven：
  - 79 tests
  - 0 failures
  - 1 skipped
- Testcontainers integration verify：
  - BUILD SUCCESS
  - 0 failures
  - 0 skipped
- Flyway V1–V8：通过
- JaCoCo：通过
- frontend typecheck/build：通过

本阶段不要继续修改 WebSocket/Nginx，除非可靠性测试明确暴露新的真实缺陷。

# 调优阶段 12：基础设施故障注入自动化与恢复闭环

## 核心目标

把此前主要依赖人工 Docker 操作的故障恢复验收，转化为：

可重复
可自动运行
数据可核验
CI 可执行
不依赖开发者真实环境

的 integration / acceptance 测试。

重点关闭当前验收报告中的：

- RabbitMQ retry / DLQ / replay 缺少真实自动化证据
- MySQL restart 后数据持久化缺少本轮自动化证据
- Redis 故障恢复只存在部分人工证据
- MinIO 故障后的对象/元数据一致性证据仍不完整
- backend restart 后恢复行为缺乏统一自动化验收

注意：

“可恢复”不等于“高可用”。

本阶段禁止将单实例 Testcontainers 故障恢复描述为：

HA
failover cluster
production SLA
zero downtime

---

# 一、先冻结现状

先执行：

git status
git diff --check

保存当前：

Maven 测试数量
integration verify
JaCoCo
Flyway
Compose
E2E 11.6 报告

不要覆盖阶段 11.6 的历史证据。

新增统一文档，例如：

docs/fault-injection-acceptance-2026-08-10.md

记录阶段 12 每一个故障场景。

---

# 二、先审查当前可靠性实现

在修改代码前审查：

## RabbitMQ

检查：

- exchange
- routing key
- queue
- retry queue
- dead-letter exchange
- dead-letter queue
- message TTL
- manual ACK
- nack/requeue
- retry 次数
- message id
- idempotency key
- consumer exception handling
- replay / compensation mechanism

给出当前真实消息流：

producer
→ main queue
→ consumer
→ retry
→ main queue
→ DLQ
→ replay

如果实际设计不同，以代码为准。

## Redis

检查：

- Redis 用途
- active session
- reminder index
- distributed lock
- cache
- Redis unavailable 时应用行为
- Redis 数据是否可以从 MySQL 重建

明确哪些 Redis 数据：

必须持久

以及哪些：

只是派生索引。

## MinIO

检查：

- upload workflow
- MySQL attachment metadata
- object key
- upload failure
- FAILED 状态
- compensation
- orphan cleanup
- delete consistency

## MySQL

检查：

- Flyway
- HikariCP
- reconnect
- transaction behavior
- restart 后连接恢复
- volume / Testcontainers persistence strategy

## Backend restart

检查：

- reminder recovery
- Rabbit consumer startup
- Redis session behavior
- scheduled jobs
- unfinished task recovery
- notification persistence

完成现状分析后再实施。

---

# 三、RabbitMQ：真实 retry → DLQ → replay

这是本阶段最高优先级。

禁止 mock RabbitMQ。

使用当前 Testcontainers RabbitMQ 实例。

构造一条：

确定性失败

但不会污染正式业务数据的消息。

推荐通过测试专用 fixture 或明确失败条件实现。

不要通过：

随机异常

制造测试。

完整流程必须验证：

1. 发布消息
2. 主队列收到
3. consumer 实际处理
4. 第一次失败
5. retry
6. 达到配置的最大 retry 次数
7. 消息进入 DLQ
8. DLQ 中可以观察到原消息
9. message id 保持可追踪
10. 执行项目真实 replay / compensation 机制
11. 消息重新处理
12. 最终业务状态成功
13. DLQ 最终状态符合设计
14. 不产生重复业务数据

必须断言：

retry count

而不是只检查：

“最后在 DLQ”。

如果 retry 次数通过 header 保存：

验证实际 header。

如果通过数据库保存：

验证对应数据库事实。

---

# 四、RabbitMQ 幂等

额外发送：

同一个 message id

两次。

验证：

consumer 两次收到消息时，

业务事实只能产生一次。

例如：

notification
audit
business state

以当前业务设计为准。

必须明确证明：

RabbitMQ at-least-once

情况下应用层幂等成立。

不要声称 RabbitMQ exactly-once。

---

# 五、RabbitMQ replay 的安全性

检查当前 replay 是否可能：

重复处理
重复通知
重复审计
重复状态变化

如果 replay 本身缺少防护：

允许修复。

但必须遵循：

最小修改。

不要为了测试重新设计 MQ 架构。

最终测试至少包含：

Retry PASS
DLQ PASS
Replay PASS
Idempotency PASS

---

# 六、Redis 故障恢复

优先使用测试隔离环境。

不要停止开发者真实 Redis。

可以根据项目现有测试体系选择：

- Testcontainers stop/start
- Docker client
- Toxiproxy

优先选择侵入最小且稳定的方案。

如果引入 Toxiproxy：

仅作为 test scope dependency。

不要引入到 production runtime。

测试至少覆盖两个不同性质的数据：

## A. Active session

正常登录
→ Redis session 存在
→ Redis 不可访问
→ 受保护接口行为符合项目设计
→ Redis 恢复

明确当前设计：

Redis 不可用时 fail-open 还是 fail-closed。

对于认证安全：

默认应保持 fail-closed。

如果当前代码并非如此：

记录并修复。

Redis 恢复后：

根据当前架构验证 session 状态。

不要伪造“旧 session 自动恢复”，除非实际实现支持。

## B. Reminder / Redis index

创建持久 reminder

确认：

MySQL 持久事实存在。

Redis 派生索引存在。

模拟 Redis 数据丢失或服务恢复。

执行项目已有：

reminder recovery / rebuild

验证：

Redis index 可以依据 MySQL 事实恢复。

核心原则：

MySQL = source of truth
Redis = rebuildable index

只有代码事实支持时才这样描述。

---

# 七、MinIO 故障和补偿

使用真实 MinIO Testcontainer。

场景 1：

MinIO 正常

→ 上传附件
→ MySQL metadata SUCCESS
→ 对象真实存在

验证：

object key
size
metadata

不要只检查 HTTP 200。

场景 2：

MinIO 不可用

→ 调用附件上传
→ API 返回符合当前设计的失败
→ MySQL metadata 为 FAILED 或当前项目定义的等价状态
→ 不出现“数据库 SUCCESS 但对象不存在”。

场景 3：

MinIO 恢复

如果项目存在：

retry / compensation

则执行真实补偿。

验证：

FAILED
→ SUCCESS

以及：

对象存在
metadata 一致。

如果项目没有自动补偿：

不要为了评分强行开发复杂任务。

可以保留：

FAILED 可观测 + 人工/后台重试入口

作为真实边界。

---

# 八、检查 orphan object

构造：

MinIO 上传成功

但数据库事务随后失败

的测试场景。

检查当前 compensation 是否删除对象。

如果已有补偿机制：

验证对象最终不存在。

如果没有：

记录：

ORPHAN_OBJECT_RISK

再评估是否需要最小修复。

禁止通过测试结束后清空 bucket 掩盖一致性问题。

---

# 九、MySQL restart persistence

本阶段必须获得真实自动化证据。

必须使用隔离数据库。

禁止停止开发者真实 MySQL。

测试准备事实数据：

至少包括：

users
tasks
notifications
attachments

如果某些数据 fixture 构造成本过高：

选择最具代表性的核心事实。

记录 restart 前：

Flyway version

users count
tasks count
notifications count
attachments count

以及若干明确业务记录 ID。

然后：

模拟 MySQL container restart。

必须是：

同一个数据库存储生命周期下的 restart

而不是：

创建一个全新的空 MySQL。

等待：

MySQL healthy

应用连接池重新获得连接。

然后验证：

Flyway version 不变

核心数据 count 不变

指定业务记录仍存在

应用 API 恢复访问。

---

# 十、验证 HikariCP / datasource 恢复

MySQL restart 之后：

不要只使用 JDBC 测试数据库。

还必须通过应用 Service / API 层完成至少一次数据库访问。

验证：

HikariCP 不需要重启整个 JVM 就可以恢复，

如果当前实际设计支持。

如果必须重启 backend：

如实记录：

APPLICATION_RESTART_REQUIRED。

不要伪造透明恢复。

---

# 十一、Backend restart recovery

在 acceptance / integration 隔离环境验证：

backend restart 前准备：

- active user/session
- reminder
- pending MQ state
- notification records

模拟应用实例重启。

验证：

1. Flyway 不重复执行破坏性迁移
2. Rabbit consumer 恢复
3. reminder scheduler/recovery 恢复
4. Redis index 按设计存在或重建
5. MySQL 事实不丢失
6. 未读通知可通过 REST 补拉

WebSocket 不要求：

连接跨 backend restart 保持不断。

正确的设计允许：

连接断开
→ frontend reconnect
→ REST 补拉。

已有阶段 11.6 WebSocket 证据，不要重新设计协议。

---

# 十二、不要依赖 sleep

故障测试中禁止大量使用固定：

Thread.sleep(5000)

等待状态。

优先：

Awaitility
polling
health condition
queue depth condition
database condition

设置合理 timeout。

超时时：

测试失败。

不要无限等待。

如果项目尚未使用 Awaitility：

允许仅 test scope 引入。

---

# 十三、测试隔离

每个 integration test：

使用唯一：

user
task
message id
attachment key

避免测试间相互污染。

Rabbit queue / message：

测试后清理或使用隔离 namespace。

MinIO object：

使用独立 prefix。

不要：

truncate 整个正式表。

---

# 十四、测试组织

保持当前两层测试策略。

快速测试：

.\mvnw.cmd test

不得因故障测试显著变慢。

可靠性测试进入：

.\mvnw.cmd "-Dtaskflow.integration=true" verify

如果故障测试非常耗时：

允许增加明确 tag/profile，例如：

taskflow.reliability=true

但 integration-security CI 必须可以执行它。

不要把可靠性测试默认永久 skip。

---

# 十五、输出统一可靠性矩阵

更新：

docs/fault-injection-acceptance-2026-08-10.md

至少包含：

| Scenario | Failure | Expected | Actual | Automated | PASS/FAIL |

场景：

Rabbit retry
Rabbit DLQ
Rabbit replay
Rabbit duplicate delivery
Redis auth unavailable
Redis reminder rebuild
MinIO unavailable upload
MinIO recovery
MinIO orphan compensation
MySQL restart
MySQL data persistence
Datasource recovery
Backend restart
Reminder recovery
Notification REST recovery

对每个场景明确：

LOCAL_SINGLE_INSTANCE

而不是 HA。

---

# 十六、测试结果要求

本阶段完成后执行：

.\mvnw.cmd test

.\mvnw.cmd "-Dtaskflow.integration=true" verify

如果单独增加 reliability profile：

也执行 reliability verify。

此外重新执行：

frontend npm run typecheck
frontend npm run build

docker compose config --quiet

docker compose -f docker-compose.acceptance.yml config --quiet

kubectl kustomize k8s

kubectl kustomize k8s/overlays/kind-production-like

git diff --check

WebSocket 11.6 不需要每次连续跑 3 轮。

但至少执行一次 Nginx proxy 9/9 smoke，确保本阶段没有破坏已有闭环。

---

# 十七、CI

将 reliability tests 接入：

integration-security

或现有合理的慢测试 workflow。

必须保证：

Rabbit retry/DLQ 测试失败
MySQL persistence 测试失败
MinIO consistency 测试失败

都会导致 CI fail。

如果远程 GitHub Actions 当前不能执行：

明确：

LOCAL_VERIFIED
REMOTE_NOT_VERIFIED

不要声称远程绿灯。

---

# 十八、禁止事项

本阶段不要：

- Redis Cluster
- RabbitMQ Cluster
- MySQL 主从
- MinIO distributed mode
- Kubernetes HA
- Kafka
- Spring Cloud
- Seata
- Chaos Mesh
- 大型混沌工程平台

当前任务是：

证明单实例环境下应用具有明确、可重复的失败和恢复语义。

不是搭建生产 HA。

---

# 十九、评分

当前项目评分：

85/100

本阶段不得机械加分。

只有真实关闭：

Rabbit retry/DLQ/replay
MySQL restart persistence
MinIO consistency
Redis recovery

中的主要证据缺口，

才能重新评估：

运行集成与恢复就绪度

评估可信度与可观测性

测试、构建与回归

三个维度。

不要修改权重。

如果部分失败：

可以保持 85。

---

# 二十、最终输出

完成后请给出：

1. 新增/修改文件；
2. 新增 reliability tests；
3. RabbitMQ retry 实际次数；
4. DLQ 实际证据；
5. replay 结果；
6. duplicate delivery 幂等结果；
7. Redis 故障行为；
8. reminder rebuild 结果；
9. MinIO FAILED / recovery / orphan 结果；
10. MySQL restart 前后事实快照；
11. datasource 是否自动恢复；
12. backend restart 恢复结果；
13. 完整 Maven 测试数量；
14. 所有失败测试；
15. LOCAL / REMOTE CI 状态；
16. 尚未关闭的可靠性风险；
17. 是否建议修改当前 85/100；
18. 如果修改，逐维给出原因。

完成阶段 12 后停止。

不要开始阶段 13。