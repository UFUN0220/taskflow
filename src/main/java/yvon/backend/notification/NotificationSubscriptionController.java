package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/** Confirms that the authenticated notification subscription is active. */
@Controller
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationSubscriptionController {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationSubscriptionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/notifications/ready")
    public void subscriptionReady(Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("WebSocket连接未认证");
        }
        messagingTemplate.convertAndSendToUser(principal.getName(), DESTINATION,
                new NotificationSubscriptionReady("SUBSCRIPTION_READY"));
    }

    public record NotificationSubscriptionReady(String notificationType) {
    }
}
