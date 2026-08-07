# 阶段 8：评论与附件

## 功能边界

阶段 8 增加任务评论和任务附件能力。`task_comment`、`task_attachment` 表已经由 V1 创建，本阶段只通过 V7 增加稳定权限编码，不重复建表。

评论接口：

- `POST /api/tasks/{taskId}/comments`：发表评论；
- `GET /api/tasks/{taskId}/comments`：按创建时间倒序分页查询。

附件接口：

- `POST /api/tasks/{taskId}/attachments`：上传附件；
- `GET /api/tasks/{taskId}/attachments`：查询可用附件元数据；
- `GET /api/tasks/{taskId}/attachments/{attachmentId}/download`：流式下载；
- `GET /api/tasks/{taskId}/attachments/{attachmentId}/presigned-url`：生成短时临时地址；
- `DELETE /api/tasks/{taskId}/attachments/{attachmentId}`：删除附件。

下载、临时地址和删除都会同时校验路径中的 `taskId`、附件归属任务以及当前用户任务数据范围，避免把可访问的附件 ID 当作跨任务访问凭证。

## 权限

V7 增加：

`task:comment:read`、`task:comment:create`、`task:attachment:read`、`task:attachment:create`、`task:attachment:delete`。

所有内置角色都拥有评论查看/创建和附件查看/上传权限。附件删除权限仅分配给 `system_admin`、`department_manager`、`project_manager`；服务层额外允许附件上传者和任务创建者删除自己的授权范围内附件。Controller 权限仅是第一道门，最终判断在服务层完成。

## 文件校验和对象键

默认允许 `pdf`、`png`、`jpg`、`jpeg`、`txt`，并要求后缀与 MIME 类型匹配；单文件默认上限 10 MB。文件名经过路径基名化、控制字符清理和长度校验，不能用于生成对象键。

MinIO 保存文件内容，MySQL 只保存 bucket、不可预测的 UUID 对象键、原始文件名、类型、大小、SHA-256 checksum 和状态。对象键形如 `tasks/{taskId}/{uuid}.{extension}`，不接受用户传入的路径片段。

## 一致性和补偿

上传采用短事务加对象存储调用的编排方式：

```text
校验任务可见性和文件
        ↓
MySQL 写入 UPLOADING 元数据
        ↓
MinIO 写对象
        ↓
MySQL 条件更新为 AVAILABLE
```

- MinIO 写入失败：元数据转为 `FAILED`，前端只收到稳定业务错误；
- MinIO 成功但元数据状态更新失败：尝试删除对象，并将元数据转为 `FAILED`；
- 删除先将元数据条件更新为 `DELETED`，再删除对象；对象删除失败时回补为 `FAILED`，保留后续补偿依据；
- 状态变更使用 `status + version + deleted` 条件，避免重复操作覆盖并发更新；
- 当前阶段记录补偿失败日志，未引入无限重试或定时清理任务，后续可由运维补偿流程处理 `FAILED` 记录。

## 运行配置

附件默认关闭，避免测试和没有 MinIO 凭据的环境误启动。启用时设置：

```properties
TASKFLOW_ATTACHMENT_ENABLED=true
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin_local
MINIO_BUCKET=taskflow-attachments
```

本地示例配置见 `application-local.yml.example`；MinIO Java SDK 使用官方示例兼容的 8.6.0 版本。

## 验收覆盖

- 评论权限、评论内容校验、SYSTEM 事件和作者批量加载；
- 附件超限、恶意路径文件名、MinIO 失败补偿；
- 附件 taskId 不匹配时拒绝访问且不调用对象存储；
- V7 权限迁移结构检查；
- 启用附件配置时 Spring Boot 上下文、Flyway V7 和 MinIO 客户端配置启动验证。
