# 面试与简历材料

## 30 秒项目介绍

这是一个 Java 17、Spring Boot、Spring Security、MyBatis-Plus、MySQL、Redis、RabbitMQ、MinIO、React/TypeScript 构建的企业任务协同模块化单体。核心难点是状态机、并发更新、数据权限和通知一致性；MySQL 保存事实，Redis 做提醒索引，RabbitMQ 做异步投递，WebSocket 提供 best-effort 实时体验，断线后通过通知表补拉。

## 推荐简历成果

1. 实现基于 `status + version` 条件更新的任务状态机和乐观并发控制，重复/过期操作返回冲突且不重复写操作日志。
2. 设计 MySQL reminder_plan、Redis ZSet、RabbitMQ 和 notification 表分层的提醒通知链路，加入手动 Ack、有限重试、死信和按消息/用户幂等。
3. 使用 Spring Security 方法权限 + Service 数据范围双重校验，覆盖 SELF、部门、部门及子部门、项目和显式 ALL 范围。
4. 集成 MinIO 附件内容与 MySQL 元数据，增加文件类型/大小/签名校验和跨系统失败补偿。
5. 建立 Maven/Testcontainers/JaCoCo、npm audit/OSV、Playwright、Compose 和 Kind 的可复核工程验证链路。

## 面试必须说清的边界

- Compose 六服务和 Kind 单节点不是生产 HA；
- WebSocket 是实时推送，不是可靠消息投递；
- OSV 当前快照无命中不等于零供应链风险；
- 单机性能基线不等于生产容量；
- “负责/参与/协作完成”必须按真实代码和分工使用，不能声称独立完成整个项目。

## 高频追问

重点准备：为什么模块化单体、Redis 为什么不是事实来源、为什么使用 Flyway、如何用旧状态和 version 防并发覆盖、RabbitMQ 为什么手动 Ack/有限重试、通知为何持久化后再推送、Cookie 为什么需要 CSRF、Kind 为什么不等于生产 Kubernetes、故障演练如何证明恢复而不是只证明容器重启。

原阶段 19 的完整 50 题、架构图和详细回答保留在 `stage19-interview-materials.md` 作为详细扩展参考；本文件负责当前可对外口径，历史版本中的过时评分和旧测试数字不再作为当前结论。
