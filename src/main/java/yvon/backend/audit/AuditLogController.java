package yvon.backend.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;

@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@ConditionalOnProperty(name = {"taskflow.audit.enabled", "taskflow.auth.enabled"}, havingValue = "true", matchIfMissing = true)
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public ApiResponse<PageResponse<AuditLogResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId) {
        return ApiResponse.success(auditLogService.page(page, size, traceId, resourceType, resourceId));
    }
}
