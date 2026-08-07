# 阶段 6：任务创建、查询与分配

## 本阶段边界

阶段 6 聚焦任务数据管理：

- 创建任务草稿；
- 创建人或授权用户编辑草稿；
- 创建人或授权用户逻辑删除草稿；
- 按标题、状态、优先级、负责人、创建人、部门、项目和时间范围分页筛选；
- 返回主负责人和协作人；
- 转交任务时校验用户状态、项目成员关系和数据范围；
- 任务写操作和操作日志在同一事务内完成。

任务状态命令已在现有任务服务中提供，但本阶段不新增任意状态修改接口。评论、附件、提醒和消息仍属于后续阶段。

## 查询与索引依据

任务列表首先通过数据范围查询得到可见任务 ID，再叠加业务筛选条件，避免把用户输入拼接成 SQL。分页返回使用一次 `task_assignee` 关联查询批量装载负责人，避免每条任务再次查询负责人。

主要索引复用 V1 已有设计：

| 查询场景 | 使用的索引 |
| --- | --- |
| 创建人+状态+创建时间 | `idx_task_creator_status_created` |
| 项目+状态+截止时间 | `idx_task_project_status_due` |
| 部门+状态+截止时间 | `idx_task_department_status_due` |
| 负责人筛选 | `idx_task_assignee_user_type` |
| 状态+截止时间 | `idx_task_status_due` |

可在真实 MySQL 中执行以下检查：

```sql
EXPLAIN SELECT id FROM task WHERE creator_id = 1 AND status = 'DRAFT' ORDER BY created_at DESC;
EXPLAIN SELECT id FROM task WHERE project_id = 1 AND status = 'IN_PROGRESS' ORDER BY due_at;
EXPLAIN SELECT task_id FROM task_assignee WHERE user_id = 1 AND deleted = 0;
```

本阶段没有为了标题模糊查询盲目新增索引；如果后续真实数据和执行计划证明标题检索是高频瓶颈，再单独评估全文检索或前缀索引。

## 并发与删除规则

编辑和删除都必须携带任务 `version`。删除使用逻辑删除，保留任务操作日志和外键关系；已经进入 `PENDING_ACCEPTANCE` 或更后流程的任务禁止删除，只能通过状态命令取消或归档。
