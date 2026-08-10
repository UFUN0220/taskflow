package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.notification.NotificationWebSocketHandshakeHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationWebSocketHandshakeHandlerTest {

    private final ProbeHandler handler = new ProbeHandler();

    @Test
    void mapsVerifiedHttpUserToIdBasedWebSocketPrincipal() {
        UserPrincipal user = user(11L, "alice");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(user, null));

        Principal principal = handler.determine(request, mock(WebSocketHandler.class));

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("11");
    }

    @Test
    void doesNotInventPrincipalWhenHandshakeIsUnauthenticated() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getPrincipal()).thenReturn(null);

        assertThat(handler.determine(request, mock(WebSocketHandler.class))).isNull();
    }

    private UserPrincipal user(Long id, String username) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus("ACTIVE");
        return new UserPrincipal(user, List.of());
    }

    private static final class ProbeHandler extends NotificationWebSocketHandshakeHandler {
        private Principal determine(ServerHttpRequest request, WebSocketHandler handler) {
            return super.determineUser(request, handler, new HashMap<>());
        }
    }
}
