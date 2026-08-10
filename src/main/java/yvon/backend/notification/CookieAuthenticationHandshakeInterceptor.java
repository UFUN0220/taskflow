package yvon.backend.notification;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import yvon.backend.auth.AuthTokenResolver;

import java.util.Map;

/** Carries the same-origin auth cookie into the STOMP session without a URL token. */
public class CookieAuthenticationHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthTokenResolver tokenResolver;

    public CookieAuthenticationHandshakeInterceptor(AuthTokenResolver tokenResolver) {
        this.tokenResolver = tokenResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String cookieHeader = request.getHeaders().getFirst(HttpHeaders.COOKIE);
        String token = tokenResolver.resolveCookieHeader(cookieHeader);
        if (token != null) {
            attributes.put(AuthTokenResolver.WEBSOCKET_COOKIE_TOKEN_ATTRIBUTE, token);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No token or credential is logged here.
    }
}
