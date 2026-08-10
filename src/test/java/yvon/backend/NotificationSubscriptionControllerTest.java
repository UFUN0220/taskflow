package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import yvon.backend.notification.NotificationSubscriptionController;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationSubscriptionControllerTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final NotificationSubscriptionController controller =
            new NotificationSubscriptionController(messagingTemplate);

    @Test
    void sendsReadyMarkerOnlyToAuthenticatedPrincipal() {
        Principal principal = () -> "11";

        controller.subscriptionReady(principal);

        verify(messagingTemplate).convertAndSendToUser(eq("11"), eq("/queue/notifications"),
                eq(new NotificationSubscriptionController.NotificationSubscriptionReady("SUBSCRIPTION_READY")));
    }

    @Test
    void rejectsUnauthenticatedReadyProbe() {
        assertThatThrownBy(() -> controller.subscriptionReady(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WebSocket连接未认证");
    }
}
