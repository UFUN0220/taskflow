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
import yvon.backend.auth.UserPrincipal;

import java.util.List;

@Component
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public NotificationWebSocketChannelInterceptor(JwtTokenService tokenService,
                                                   UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } else if (!StompCommand.DISCONNECT.equals(accessor.getCommand()) && accessor.getUser() == null) {
            throw new IllegalArgumentException("WebSocket连接未认证");
        }
        return message;
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
