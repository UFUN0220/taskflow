# Stage 2 backend conventions

## API response and errors

Every JSON API returns `ApiResponse<T>` with `code`, `message`, `data`, `traceId`, and `timestamp`. A successful response uses code `0`. Client-visible error codes are defined in `BusinessErrorCode`; business services throw `BusinessException` instead of returning ad-hoc error maps.

`GlobalExceptionHandler` converts validation, authentication, authorization, business, and unknown exceptions into the same envelope. Unknown exception details are logged on the server with the trace ID, while the client receives only `COMMON_500` and a generic message.

## DTO, VO, and Entity boundaries

- Entity: persistence model only. It may extend `AuditEntity`, but it must not be returned directly by a controller.
- DTO: request input only. Put Jakarta Validation annotations on DTO fields and validate at the controller boundary.
- VO: response output only. Expose only fields needed by the client; do not leak passwords, tokens, internal audit data, or persistence-only fields.
- Conversion: use explicit module-local assembler/converter methods such as `toEntity()` and `toVO()`. Do not add a mapping framework until repeated conversion justifies it.

## MyBatis-Plus rules

`MybatisPlusConfig` enables MySQL pagination through `MybatisPlusInterceptor`. Mutable entities use `AuditEntity` for ID, timestamps, operator IDs, optimistic-lock `version`, and logical-delete `deleted`. `AuditMetaObjectHandler` fills timestamps and default version/deletion values. Operator IDs remain null until the authentication context is implemented; they must not be populated with a guessed user.

Pagination queries must define a stable order, and repository methods should use the smallest required projection. New indexes require a documented query scenario; do not add indexes to every column.

## Trace ID

`TraceIdFilter` accepts a safe `X-Trace-Id` header or generates one, stores it in request-local context and MDC, and echoes it in the response. The context is cleared in `finally` so thread-pool reuse cannot leak IDs between requests.

## Verification

`BackendApplicationTests` checks the unified health response. `GlobalExceptionHandlerTest` checks invalid parameters, stable business errors, generic unknown errors, and caller-supplied trace IDs. `HealthServiceTest` covers the service layer directly. Flyway V1/V2 are checked statically and have also been executed successfully against a fresh MySQL 8.4 container.
