# AGENTS.md

## Project

本项目是企业任务协同与流程管理平台，采用Java 17、Spring Boot、Spring Security、MyBatis-Plus、MySQL、Redis、RabbitMQ、MinIO、React和Docker Compose开发。

项目优先采用模块化单体架构。禁止为了展示技术而无必要拆分微服务。

## Execution Rules

1. 执行任务前先检查现有代码、README、CHANGELOG和相关docs。
2. 不重复创建已经存在的模块、类、配置和数据库表。
3. 不覆盖用户已有改动。
4. 不删除数据库、Docker卷、配置文件或用户文件，除非用户明确要求。
5. 每次只完成当前指定阶段。
6. 当前阶段构建或测试失败时，不得宣称完成。
7. 不提前实现后续阶段功能。
8. 所有新增功能必须补充必要测试。
9. 所有数据库变更必须通过Flyway。
10. 所有重要变更更新README、CHANGELOG和对应docs。
11. 不虚构性能指标、用户数量或生产运行结果。
12. 不将团队成果描述为个人独立完成。
13. 对任何无法验证的结果明确标注未验证。
14. 遇到环境冲突先报告，不得通过删除数据或绕过安全配置解决。

## Backend Rules

- Controller不得包含核心业务逻辑。
- Entity、DTO和VO不得无理由混用。
- 所有外部输入使用Jakarta Validation。
- 所有关键写操作明确事务边界。
- 所有状态变化由后端校验。
- 所有权限必须由后端执行。
- Redis不得作为核心业务唯一数据源。
- MQ消费者必须实现幂等。
- 禁止无限消息重试。
- 文件内容存储在MinIO，MySQL只保存元数据。
- 日志不得输出密码、完整Token或其他敏感信息。
- 不得捕获异常后静默忽略。
- 不得在事务中执行不必要的长时间网络调用。
- 使用Java 17兼容语法。

## Frontend Rules

- 使用React、TypeScript和Vite。
- 不追求复杂视觉效果。
- 权限按钮隐藏仅用于用户体验，不能替代后端鉴权。
- 统一处理401、403和业务异常。
- 写操作需要重复提交保护。
- 不引入无必要的大型状态管理框架。

## Verification

每次变更后，根据影响范围执行：

```bash
./mvnw test
npm run build
docker compose config --quiet
```

Windows环境使用：

```powershell
.\mvnw.cmd test
npm run build
docker compose config --quiet
```

涉及数据库、Redis、RabbitMQ或MinIO时，补充对应集成测试。

## Delivery

每次完成任务后汇报：

- 实现内容；
- 修改文件；
- 核心逻辑；
- 测试结果；
- 遗留问题；
- 文档更新；
- 下一步建议；
- 需要用户重点理解的代码。