package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Acceptance-only endpoint for retrieving non-sensitive notification delivery evidence. */
@RestController
@Profile("acceptance")
@ConditionalOnProperty(name = "taskflow.websocket.diagnostics.enabled", havingValue = "true")
@RequestMapping("/api/acceptance/notification-diagnostics")
public class NotificationDiagnosticsController {

    private final NotificationDeliveryDiagnostics diagnostics;

    public NotificationDiagnosticsController(NotificationDeliveryDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping("/{notificationId}")
    @PreAuthorize("hasAuthority('notification:read')")
    public NotificationDeliveryDiagnostics.TraceSnapshot snapshot(@PathVariable Long notificationId) {
        return diagnostics.snapshot(notificationId);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('notification:write')")
    public void clear() {
        diagnostics.clear();
    }
}
