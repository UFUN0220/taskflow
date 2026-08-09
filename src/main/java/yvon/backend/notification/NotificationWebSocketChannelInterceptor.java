package yvon.backend.notification;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import yvon.backend.auth.JwtTokenService;
import yvon.backend.auth.AuthSessionService;
import yvon.backend.auth.UserPrincipal;

import java.util.List;
import java.security.Principal;
import java.util.Map;

@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHENTICATED_PRINCIPAL =
            NotificationWebSocketChannelInterceptor.class.getName() + ".principal";

    private final JwtTokenService tokenService;
    private final AuthSessionService sessionService;
    private final UserDetailsService userDetailsService;

    public NotificationWebSocketChannelInterceptor(JwtTokenService tokenService,
                                                   AuthSessionService sessionService,
                                                   UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Principal principal = authenticate(accessor);
            accessor.setUser(principal);
            storePrincipal(accessor, principal);
            return authenticatedMessage(message, accessor);
        } else if (!StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                principal = storedPrincipal(accessor);
                if (principal != null) {
                    accessor.setUser(principal);
                    return authenticatedMessage(message, accessor);
                }
            }
            if (principal == null) {
                throw new IllegalArgumentException("WebSocket连接未认证");
            }
        }
        return message;
    }

    private Message<?> authenticatedMessage(Message<?> message, StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private void storePrincipal(StompHeaderAccessor accessor, Principal principal) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) {
            attributes.put(AUTHENTICATED_PRINCIPAL, principal);
        }
    }

    private Principal storedPrincipal(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object principal = attributes.get(AUTHENTICATED_PRINCIPAL);
        return principal instanceof Principal value ? value : null;
    }

    private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String authorization = firstHeader(accessor, "Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("WebSocket连接缺少认证信息");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("WebSocket连接缺少认证信息");
        }
        try {
            Claims claims = tokenService.parse(token);
            if (!sessionService.isActive(claims)) {
                throw new IllegalArgumentException("WebSocket会话已失效");
            }
            UserDetails details = userDetailsService.loadUserByUsername(claims.getSubject());
            Number tokenUserId = claims.get("uid", Number.class);
            if (!(details instanceof UserPrincipal principal)
                    || tokenUserId == null
                    || principal.userId() == null
                    || tokenUserId.longValue() != principal.userId().longValue()
                    || !principal.isEnabled()) {
                throw new IllegalArgumentException("WebSocket用户身份无效");
            }
            NotificationWebSocketPrincipal websocketPrincipal =
                    new NotificationWebSocketPrincipal(principal.userId(), principal.getUsername());
            return new UsernamePasswordAuthenticationToken(websocketPrincipal, null, details.getAuthorities());
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            throw new IllegalArgumentException("WebSocket认证失败", exception);
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
