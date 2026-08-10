# Stage 3 authentication and authorization

## Scope

Stage 3 adds stateless JWT authentication, password verification with BCrypt, current-user context, stable permission authorities, protected user and department read APIs, and environment-controlled development administrator bootstrapping.

## Login flow

1. `POST /api/auth/login` accepts a username or employee number and password.
2. `AuthUserDetailsService` loads only active users and joins active roles and permissions.
3. `DaoAuthenticationProvider` verifies the BCrypt password.
4. `JwtTokenService` issues a signed token containing the user ID and subject.
5. `JwtAuthenticationFilter` validates the token on later requests and reloads the current user, so disabled users and changed permissions do not remain trusted until token expiry.

## Development administrator

The application never stores a plaintext password in Flyway. To create a local administrator, set `TASKFLOW_BOOTSTRAP_ADMIN_ENABLED=true` and `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD` before starting the backend. The initializer is idempotent and assigns the built-in `system_admin` role.

Do not put a real password in this document or in Flyway. Use a local-only secret manager or an ignored environment file, and rotate the bootstrap password after the first login.

## Permission boundary

V3 seeds stable permission codes such as `auth:me`, `user:read`, and `department:read`. Controllers use backend `@PreAuthorize` checks; hiding a frontend button is not an authorization mechanism. Data-scope filtering and role/permission management APIs remain later work.

## 浏览器认证与兼容客户端

浏览器登录成功后，后端通过 `Set-Cookie` 写入 `TASKFLOW_ACCESS` HttpOnly Cookie；React 的 `fetch` 使用 `credentials: include`，不会把 JWT 写入 `localStorage`。写请求先获取 `/api/auth/csrf`，再提交 `X-XSRF-TOKEN`，因此 Cookie 认证不是“关闭 CSRF 的 JWT Cookie”。dev/test/acceptance 使用非 Secure Cookie 适配本地 HTTP，prod 配置 `Secure=true`，并保持 `SameSite=Lax`。

登录 JSON 仍返回 `accessToken`，这是给性能脚本、Testcontainers 和非浏览器 Bearer 客户端的兼容接口。正式浏览器流程忽略该字段；后端过滤器仍支持 `Authorization: Bearer`，并以 Redis active-session、JWT 过期、角色权限和数据范围为最终校验。

同源 WebSocket 连接自动携带 Cookie，STOMP CONNECT 不再放入 JWT；后端握手拦截器只在内存会话属性中传递凭据，不记录或拼接 URL 参数。兼容客户端仍可在 STOMP CONNECT Header 使用 Bearer。

## 当前验收边界

当前实现已验证登录、BCrypt 密码校验、JWT 签发、当前用户查询、禁用用户重新加载失效和受保护接口的 401/403 边界。认证仍使用 `SessionCreationPolicy.STATELESS` 的请求模型，但每个 JWT 带有唯一 `jti`，`AuthSessionService` 在 Redis 中保存带 JWT 过期时间的活动会话标记。

`POST /api/auth/logout` 会删除当前 `jti` 的 Redis 会话；HTTP 过滤器和 WebSocket CONNECT 校验都会拒绝已经删除或过期的会话。前端退出会先请求后端，再清理本地 Token；如果后端暂时不可达，页面仍会退出，但会提示服务端未确认，令牌只能等待过期。登录失败按账号和来源地址使用 Redis 执行默认 10 次/60 秒的有限窗口控制。

已补充会话注册、TTL、撤销、Cookie 属性、CSRF matcher、Cookie/CSRF 过滤器和限流窗口测试；阶段 9 完整 Maven 回归为 75 项执行、0 失败、1 项可选 Testcontainers 跳过，显式 integration verify 75/0/0 通过。真实 acceptance Compose 已通过 HttpOnly/SameSite Cookie 登录、Cookie `/me`、任务列表、CSRF 登出和旧会话 401。完整 Playwright 仍有重复提交与通知链路失败，因此不把浏览器全量回归描述为通过。
