# 阶段 7：状态机与并发控制

## 范围

- 固定任务状态：`DRAFT`、`PENDING_ACCEPTANCE`、`IN_PROGRESS`、`PENDING_REVIEW`、`REJECTED`、`COMPLETED`、`CANCELLED`、`ARCHIVED`；
- 通过独立命令接口执行提交、接受、开始、提交审核、驳回、完成、取消、归档和转交；
- 禁止通过通用接口直接修改 `status`；
- 使用旧状态与 `version` 的条件更新防止并发覆盖；
- 任务更新和操作日志处于同一事务边界。

## 核心调用链

```text
TaskController
  -> TaskService.transition
    -> requireVisible / canOperate
    -> TaskStateMachine.target
    -> TaskMapper.updateStatusWithVersion
       WHERE id + old status + old version + deleted=0
    -> task_assignee.accepted_at（仅接受命令）
    -> task_operation_log
```

转交调用 `TaskMapper.updateStatusWithVersion` 将状态原值写回并递增版本，再删除并重建 `task_assignee`，最后记录 `TRANSFER` 日志。这样“转交”和“提交/接受”等写操作不会共享同一旧版本而互相覆盖。

## 冲突语义

阶段 7 采用显式冲突而非隐式幂等：同一个旧版本的第一次命令成功后版本会递增；相同请求再次到达时，因旧版本或旧状态不再匹配，返回稳定错误码 `COMMON_409`，不重复写操作日志。非法状态流转同样返回 `COMMON_409`。

核心 SQL 不新增索引，也不新增 Flyway 迁移：任务表已有主键、状态和版本字段，阶段 7 只收紧更新条件，Flyway 版本仍为 V6。

## 验证与边界

- 单元测试证明 Mapper 更新方法携带旧状态和旧版本，并证明冲突分支不会写操作日志；
- 真实数据库启动验证 Flyway V1-V6 校验通过，应用能够启动；
- 多实例部署时仍需让所有实例使用同一 MySQL 数据库；Redis 或 JVM 内存不能替代这条数据库条件更新；
- 当前阶段未引入分布式锁，冲突处理由数据库行更新结果承担。
