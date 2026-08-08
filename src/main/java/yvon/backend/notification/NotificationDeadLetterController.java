package yvon.backend.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;
import yvon.backend.audit.AuditAction;

@RestController
@Validated
@RequestMapping("/api/admin/notification-dead-letters")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeadLetterController {

    private final NotificationDeadLetterService service;

    public NotificationDeadLetterController(NotificationDeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:dead-letter:read')")
    public ApiResponse<PageResponse<NotificationDeadLetterResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false)
            @jakarta.validation.constraints.Pattern(regexp = "RETRYING|DEAD|REPLAYED") String status) {
        return ApiResponse.success(service.page(page, size, status));
    }

    @PostMapping("/{deadLetterId}/replay")
    @PreAuthorize("hasAuthority('notification:dead-letter:replay')")
    @AuditAction(resourceType = "NOTIFICATION_DEAD_LETTER", action = "REPLAY")
    public ApiResponse<Void> replay(@PathVariable @Min(1) Long deadLetterId) {
        service.replay(deadLetterId);
        return ApiResponse.success(null);
    }
}
