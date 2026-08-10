package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import yvon.backend.auth.UserPrincipal;

import java.security.Principal;
import java.util.Map;

/**
 * Keeps the broker user-session key aligned with the notification user ID.
 * The ID is taken only from the already verified HTTP authentication context;
 * the browser cannot provide or override it.
 */
@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Principal requestPrincipal = request.getPrincipal();
        UserPrincipal userPrincipal = verifiedUser(requestPrincipal);
        if (userPrincipal != null) {
            return new NotificationWebSocketPrincipal(userPrincipal.userId(), userPrincipal.getUsername());
        }
        return super.determineUser(request, wsHandler, attributes);
    }

    private UserPrincipal verifiedUser(Principal requestPrincipal) {
        if (requestPrincipal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        return null;
    }
}
