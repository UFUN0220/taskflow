package yvon.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Acceptance-only delivery evidence. It deliberately records identifiers and
 * routing metadata, never notification content or authentication material.
 */
@Component
@Profile("acceptance")
@ConditionalOnProperty(name = "taskflow.websocket.diagnostics.enabled", havingValue = "true")
public class NotificationDeliveryDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryDiagnostics.class);
    private static final int MAX_TRACES = 2_000;

    private final Map<Long, List<Checkpoint>> traces = new ConcurrentHashMap<>();

    public void persisted(NotificationResponse notification, Long userId) {
        record(new Checkpoint("C1_PERSISTED", notification.notificationId(), notification.sourceMessageId(),
                userId, null, null, null, null, null, Instant.now(), 0, List.of()));
    }

    public void dispatchRequested(NotificationResponse notification, Long userId, String destination,
                                  int sessionCount, List<String> subscriptionDestinations) {
        record(new Checkpoint("C2_DISPATCH_REQUESTED", notification.notificationId(), notification.sourceMessageId(),
                userId, destination, null, null, Thread.currentThread().getName(), null, Instant.now(),
                sessionCount, subscriptionDestinations));
    }

    public void brokerOutbound(Long notificationId, String sourceMessageId, Long userId, String destination,
                               String sessionId, String principalName, String stompCommand) {
        record(new Checkpoint("C3_BROKER_OUTBOUND", notificationId, sourceMessageId, userId, destination,
                sessionId, principalName, Thread.currentThread().getName(), stompCommand, Instant.now(), 0, List.of()));
    }

    public void transportOutbound(Long notificationId, String sourceMessageId, String sessionId,
                                  String frameType) {
        record(new Checkpoint("C4_SERVER_WEBSOCKET_OUTBOUND", notificationId, sourceMessageId, null, null,
                sessionId, null, Thread.currentThread().getName(), frameType, Instant.now(), 0, List.of()));
    }

    public TraceSnapshot snapshot(Long notificationId) {
        List<Checkpoint> checkpoints = traces.get(notificationId);
        if (checkpoints == null) {
            return new TraceSnapshot(notificationId, List.of());
        }
        return new TraceSnapshot(notificationId, List.copyOf(checkpoints));
    }

    public void clear() {
        traces.clear();
    }

    private void record(Checkpoint checkpoint) {
        traces.computeIfAbsent(checkpoint.notificationId(), ignored -> new CopyOnWriteArrayList<>())
                .add(checkpoint);
        while (traces.size() > MAX_TRACES) {
            Long first = traces.keySet().stream().findFirst().orElse(null);
            if (first == null || traces.remove(first) == null) {
                break;
            }
        }
        log.debug("notification delivery checkpoint={}, notificationId={}, sourceMessageId={}, userId={}, "
                        + "destination={}, sessionId={}, principal={}, stompCommand={}, sessionCount={}, "
                        + "subscriptions={}, thread={}",
                checkpoint.checkpoint(), checkpoint.notificationId(), checkpoint.sourceMessageId(), checkpoint.userId(),
                checkpoint.destination(), checkpoint.sessionId(), checkpoint.principalName(), checkpoint.stompCommand(),
                checkpoint.sessionCount(),
                checkpoint.subscriptionDestinations(), checkpoint.threadName());
    }

    public record TraceSnapshot(Long notificationId, List<Checkpoint> checkpoints) {
    }

    public record Checkpoint(String checkpoint, Long notificationId, String sourceMessageId, Long userId,
                             String destination, String sessionId, String principalName, String threadName,
                             String stompCommand, Instant timestamp, int sessionCount,
                             List<String> subscriptionDestinations) {
        public Checkpoint {
            subscriptionDestinations = subscriptionDestinations == null
                    ? List.of() : Collections.unmodifiableList(new ArrayList<>(subscriptionDestinations));
        }
    }
}
