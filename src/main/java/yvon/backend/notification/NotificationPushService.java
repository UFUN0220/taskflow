package yvon.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationPushService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushService.class);
    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        push(event.userId(), event.notification());
    }

    public void push(Long userId, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), DESTINATION, notification);
        } catch (RuntimeException exception) {
            log.warn("Notification WebSocket push failed, userId={}, notificationId={}, aggregateId={}",
                    userId, notification.notificationId(), notification.aggregateId(), exception);
        }
    }
}
