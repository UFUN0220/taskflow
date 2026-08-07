# 阶段 0 测试说明

## 自动化验证

```powershell
.\mvnw.cmd test
Set-Location frontend; npm run build
Set-Location ..; docker compose config --quiet
```

后端测试验证 Spring 上下文、`/api/health` 返回 `UP`，以及 V1 Flyway 文件包含全部核心表、关键约束和索引。前端构建验证 TypeScript 编译和 Vite 产物生成。Compose 配置验证服务、端口、变量和健康检查声明可以被 Docker Compose 解析。

阶段 6 增加了 V6 迁移结构测试、草稿编辑/删除服务测试和分页批量负责人加载测试；阶段 7 增加了状态机合法性、旧状态 + version 条件更新、重复/并发冲突不写孤儿日志测试；阶段 8 增加了 V7 权限迁移、评论服务、附件校验、任务路径隔离和 MinIO 失败补偿测试；阶段 9 增加了提醒计划生成、终态取消、Redis 重建、分布式锁和 RabbitMQ 发布失败测试。真实数据库验证应确认 V1 至 V7 按序成功；启用提醒配置时还应验证 Redis ZSet、RabbitMQ 队列声明和健康启动。提醒消息消费者属于阶段10，当前未加入。
