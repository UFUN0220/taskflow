package yvon.backend.notification;

import java.security.Principal;

/**
 * WebSocket destinations use the immutable user ID as the principal name.
 * The browser never supplies this value; it is derived from the verified JWT.
 */
public final class NotificationWebSocketPrincipal implements Principal {

    private final Long userId;
    private final String username;

    public NotificationWebSocketPrincipal(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
