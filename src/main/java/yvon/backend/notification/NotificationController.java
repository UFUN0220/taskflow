package yvon.backend.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;

@RestController
@Validated
@RequestMapping("/api/notifications")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<PageResponse<NotificationResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "UNREAD|READ") String status,
            org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(notificationService.page(principal(authentication).userId(), page, size, status));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Long> unreadCount(org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(notificationService.unreadCount(principal(authentication).userId()));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAuthority('notification:write')")
    public ApiResponse<Void> markRead(@PathVariable @Min(1) Long notificationId,
                                      org.springframework.security.core.Authentication authentication) {
        notificationService.markRead(notificationId, principal(authentication).userId());
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:write')")
    public ApiResponse<Integer> markAllRead(org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(notificationService.markAllRead(principal(authentication).userId()));
    }

    private UserPrincipal principal(org.springframework.security.core.Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
