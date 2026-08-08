package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userId(accessor.getUser());
        if (userId != null) {
            sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(accessor.getSessionId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userId(event.getUser());
        if (userId == null) {
            return;
        }
        sessionsByUser.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.remove(event.getSessionId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public boolean isConnected(Long userId) {
        return !sessionsByUser.getOrDefault(userId, Collections.emptySet()).isEmpty();
    }

    public int sessionCount(Long userId) {
        return sessionsByUser.getOrDefault(userId, Collections.emptySet()).size();
    }

    private Long userId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
