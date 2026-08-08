# 阶段 10：RabbitMQ 异步通知

## 功能边界

阶段 10 消费阶段 9 的提醒消息和任务状态变化消息，将通知写入 `notification`，并提供用户查询/已读接口。WebSocket 实时推送留到阶段 11。

提醒通知发送给任务当前有效的主负责人和协作人；任务状态变化通知发送给当前有效的负责人、协作人和创建人。终态任务收到延迟提醒消息时直接确认消息，不再生成通知。

## 消息结构

当前队列支持两种事件：

- `REMINDER_DUE`：`messageId` 使用 `reminder_plan.id`，包含 `planId`、`taskId`、提醒类型和触发时间；
- `TASK_STATUS_CHANGED`：`messageId` 使用 `task:<taskId>:<afterVersion>`，包含任务状态前后值、操作者和发生时间。

两种消息都包含 `eventType` 和 `messageId`，traceId 放在 RabbitMQ message header，并在死信记录中保留。发布端启用 publisher confirm、mandatory returns 和 correlation data。

## 幂等、Ack 和失败处理

消费者使用手动 Ack。通知表增加 `(source_message_id, user_id)` 唯一索引，同一事件对同一用户重复投递不会产生重复通知。

单条消息最多处理 3 次：首次失败后发送到 TTL 重试队列，重试队列到期后回到主队列；达到上限后写入 `notification_dead_letter`，发送到死信队列并确认原消息。数据库或重试投递失败时不重新入队，避免无限重试，并记录明确错误原因。

死信接口：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/admin/notification-dead-letters` | `notification:dead-letter:read` | 分页查看死信及错误原因 |
| POST | `/api/admin/notification-dead-letters/{id}/replay` | `notification:dead-letter:replay` | 管理员重新投递死信 |

重新投递沿用原 `messageId`，因此即使补偿请求重复执行，也由通知唯一键保证最终幂等。

## 用户通知接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/notifications` | `notification:read` | 查询当前用户通知，可按 `UNREAD`/`READ` 筛选 |
| GET | `/api/notifications/unread-count` | `notification:read` | 查询未读数量 |
| PATCH | `/api/notifications/{id}/read` | `notification:write` | 标记单条已读 |
| POST | `/api/notifications/read-all` | `notification:write` | 当前用户全部标记已读 |

V8 只新增幂等字段、死信表和权限种子，不修改 V1-V7 已应用迁移文件。
