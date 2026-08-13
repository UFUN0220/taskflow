package yvon.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Acceptance-only observation at the actual WebSocket send boundary. */
@Component
@Profile("acceptance")
@ConditionalOnProperty(name = "taskflow.websocket.diagnostics.enabled", havingValue = "true")
public class NotificationWebSocketTransportDiagnostics implements WebSocketHandlerDecoratorFactory {

    private final NotificationDeliveryDiagnostics diagnostics;
    private final ObjectMapper objectMapper;
    private final Map<String, ObservedSession> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketTransportDiagnostics(NotificationDeliveryDiagnostics diagnostics,
                                                     ObjectMapper objectMapper) {
        this.diagnostics = diagnostics;
        this.objectMapper = objectMapper;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                ObservedSession observed = new ObservedSession(session);
                sessions.put(session.getId(), observed);
                super.afterConnectionEstablished(observed);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                super.handleMessage(session, message);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                ObservedSession observed = sessions.remove(session.getId());
                super.afterConnectionClosed(observed == null ? session : observed, closeStatus);
            }
        };
    }

    private final class ObservedSession extends WebSocketSessionDecorator {
        private ObservedSession(WebSocketSession delegate) {
            super(delegate);
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            recordOutbound(getId(), message);
            super.sendMessage(message);
        }
    }

    private void recordOutbound(String sessionId, WebSocketMessage<?> message) {
        String payload = payload(message);
        if (payload == null) return;
        String body = payload;
        int separator = payload.indexOf("\n\n");
        if (separator >= 0) body = payload.substring(separator + 2);
        body = body.replace("\0", "").trim();
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode id = node.get("notificationId");
            if (id != null && id.canConvertToLong()) {
                JsonNode source = node.get("sourceMessageId");
                diagnostics.transportOutbound(id.longValue(), source == null ? null : source.asText(),
                        sessionId, message.getClass().getSimpleName());
            }
        } catch (Exception ignored) {
            // CONNECTED and SUBSCRIPTION_READY frames do not contain notificationId.
        }
    }

    private String payload(WebSocketMessage<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof String text) return text;
        if (payload instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (payload instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return null;
    }
}
