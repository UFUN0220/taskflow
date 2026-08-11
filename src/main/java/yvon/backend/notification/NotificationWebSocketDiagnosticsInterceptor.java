package yvon.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/** Observes actual messages on Spring's client outbound channel in acceptance only. */
@Component
@Profile("acceptance")
@ConditionalOnProperty(name = "taskflow.websocket.diagnostics.enabled", havingValue = "true")
public class NotificationWebSocketDiagnosticsInterceptor implements ChannelInterceptor {

    private final NotificationDeliveryDiagnostics diagnostics;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketDiagnosticsInterceptor(NotificationDeliveryDiagnostics diagnostics,
                                                       ObjectMapper objectMapper) {
        this.diagnostics = diagnostics;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        NotificationCorrelation correlation = correlation(message.getPayload());
        if (correlation.notificationId() == null) {
            return message;
        }
        Principal principal = accessor.getUser();
        Long userId = parseUserId(principal == null ? null : principal.getName());
        diagnostics.brokerOutbound(correlation.notificationId(), correlation.sourceMessageId(), userId,
                accessor.getDestination(), accessor.getSessionId(), principal == null ? null : principal.getName(),
                accessor.getCommand() == null ? null : accessor.getCommand().name());
        return message;
    }

    private NotificationCorrelation correlation(Object payload) {
        if (payload instanceof NotificationResponse notification) {
            return new NotificationCorrelation(notification.notificationId(), notification.sourceMessageId());
        }
        try {
            byte[] bytes = payload instanceof byte[] value ? value
                    : payload instanceof String value ? value.getBytes(StandardCharsets.UTF_8) : null;
            if (bytes == null) return NotificationCorrelation.empty();
            JsonNode node = objectMapper.readTree(bytes);
            JsonNode id = node.get("notificationId");
            return id == null || !id.canConvertToLong()
                    ? NotificationCorrelation.empty()
                    : new NotificationCorrelation(id.longValue(), text(node, "sourceMessageId"));
        } catch (Exception ignored) {
            return NotificationCorrelation.empty();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long parseUserId(String value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record NotificationCorrelation(Long notificationId, String sourceMessageId) {
        private static NotificationCorrelation empty() {
            return new NotificationCorrelation(null, null);
        }
    }
}
