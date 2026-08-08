# Stage 14：自动化测试

## 范围

阶段 14 以回归测试为主，不新增业务功能。测试覆盖按风险拆分为以下类别：

- 认证与 Token：登录成功、错误凭据、过期 JWT、格式非法 JWT、无效 Token。
- 权限与数据范围：RBAC 拒绝、部门数据权限、用户/角色/部门管理权限。
- 任务一致性：非法状态流转、状态机边界、乐观锁冲突、草稿与任务关键操作。
- 消息可靠性：通知消费者幂等、重复消息、有限重试和死信补偿。
- 提醒恢复：提醒取消、失败恢复、Redis 索引与调度扫描。
- 附件安全：任务附件归属校验、未授权读取/删除拒绝。
- 审计一致性：成功与失败操作的审计记录、事务边界和敏感值排除。
- 基础设施烟测：可选启动 MySQL、Redis、RabbitMQ、MinIO，并在全新 MySQL 上执行 Flyway 迁移。

## 代表性测试位置

- `src/test/java/yvon/backend/AuthControllerTest.java`
- `src/test/java/yvon/backend/JwtTokenServiceTest.java`
- `src/test/java/yvon/backend/TaskStateMachineTest.java`
- `src/test/java/yvon/backend/TaskServiceStateConcurrencyTest.java`
- `src/test/java/yvon/backend/NotificationMessageConsumerTest.java`
- `src/test/java/yvon/backend/ReminderSchedulerTest.java`
- `src/test/java/yvon/backend/TaskAttachmentServiceTest.java`
- `src/test/java/yvon/backend/Stage14ContainerEnvironmentTest.java`

其余服务测试位于同一测试源集，覆盖角色、数据范围、任务、评论、附件、提醒、通知、审计和健康检查等模块。

## 执行命令

默认回归测试不要求 Docker：

```powershell
.\mvnw.cmd test
```

阶段 14 的基础设施烟测需要本机 Docker，并且必须显式开启：

```powershell
.\mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage14ContainerEnvironmentTest" test
```

如果当前 PowerShell 仍拆分参数，可使用停止解析符：

```powershell
.\mvnw.cmd --% -Dtaskflow.integration=true -Dtest=Stage14ContainerEnvironmentTest test
```

当前默认验证结果：57 项测试执行，0 失败，1 项跳过。跳过项是显式集成测试，未设置 `taskflow.integration` 时按设计跳过。

## 未覆盖与环境说明

- 本阶段未在当前默认命令中启动容器，因此尚未宣称真实 MySQL、Redis、RabbitMQ、MinIO 联调已通过。
- `Stage14ContainerEnvironmentTest` 验证容器启动、Redis PING、RabbitMQ 连接地址、MinIO API 端口以及全新 MySQL 的 Flyway 迁移；它不是完整应用端到端测试。
- 真实 RabbitMQ 重复投递、真实数据库并发压力、浏览器端登录联调和生产规模性能仍需在具备 Docker/测试环境时补充验证。
