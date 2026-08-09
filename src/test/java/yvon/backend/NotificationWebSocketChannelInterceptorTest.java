package yvon.backend;

import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import yvon.backend.auth.AuthProperties;
import yvon.backend.auth.AuthSessionService;
import yvon.backend.auth.JwtTokenService;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.notification.NotificationWebSocketChannelInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class NotificationWebSocketChannelInterceptorTest {

    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final AuthProperties properties = properties();
    private final JwtTokenService tokenService = new JwtTokenService(properties);
    private final AuthSessionService sessionService = mock(AuthSessionService.class);
    private final NotificationWebSocketChannelInterceptor interceptor =
            new NotificationWebSocketChannelInterceptor(tokenService, sessionService, userDetailsService);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void acceptsJwtUidWhenParserReturnsDifferentNumberImplementation() {
        UserPrincipal user = user(11L, "alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(user);
        when(sessionService.isActive(any(Claims.class))).thenReturn(true);
        String token = tokenService.issue(user);

        Message<?> message = connect("Bearer " + token);
        Message<?> result = interceptor.preSend(message, channel);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("11");
    }

    @Test
    void clientCannotSendSubscribeWithoutAnAuthenticatedConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WebSocket连接未认证");
    }

    private Message<?> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", authorization);
        accessor.setSessionId("session-1");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UserPrincipal user(Long id, String username) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus("ACTIVE");
        return new UserPrincipal(user, List.of(new SimpleGrantedAuthority("notification:read")));
    }

    private AuthProperties properties() {
        AuthProperties result = new AuthProperties();
        result.setJwtSecret("01234567890123456789012345678901");
        result.setJwtExpirationMinutes(30);
        return result;
    }
}
