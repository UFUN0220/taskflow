package yvon.backend.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String traceId,
        Long operatorId,
        String resourceType,
        String resourceId,
        String action,
        String result,
        String requestMethod,
        String requestUri,
        String ipAddress,
        String detailJson,
        LocalDateTime occurredAt
) {
}
