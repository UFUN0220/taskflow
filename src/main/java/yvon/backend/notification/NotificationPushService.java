package yvon.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationPushService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushService.class);
    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationDeliveryDiagnostics diagnostics;
    private final SimpUserRegistry userRegistry;

    public NotificationPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.diagnostics = null;
        this.userRegistry = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationPushService(SimpMessagingTemplate messagingTemplate,
                                   ObjectProvider<NotificationDeliveryDiagnostics> diagnosticsProvider,
                                   ObjectProvider<SimpUserRegistry> userRegistryProvider) {
        this.messagingTemplate = messagingTemplate;
        this.diagnostics = diagnosticsProvider == null ? null : diagnosticsProvider.getIfAvailable();
        this.userRegistry = userRegistryProvider == null ? null : userRegistryProvider.getIfAvailable();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        push(event.userId(), event.notification());
    }

    public void push(Long userId, NotificationResponse notification) {
        try {
            if (diagnostics != null) {
                SimpUser user = userRegistry == null ? null : userRegistry.getUser(String.valueOf(userId));
                List<String> subscriptions = user == null ? List.of() : user.getSessions().stream()
                        .flatMap(session -> session.getSubscriptions().stream())
                        .map(subscription -> subscription.getDestination())
                        .toList();
                int sessionCount = user == null ? 0 : user.getSessions().size();
                diagnostics.dispatchRequested(notification, userId, DESTINATION, sessionCount, subscriptions);
            }
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), DESTINATION, notification);
        } catch (RuntimeException exception) {
            log.warn("Notification WebSocket push failed, userId={}, notificationId={}, aggregateId={}",
                    userId, notification.notificationId(), notification.aggregateId(), exception);
        }
    }

}
