package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(NotificationWebSocketProperties.class)
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final NotificationWebSocketProperties properties;
    private final NotificationWebSocketChannelInterceptor channelInterceptor;

    public NotificationWebSocketConfiguration(NotificationWebSocketProperties properties,
                                               NotificationWebSocketChannelInterceptor channelInterceptor) {
        this.properties = properties;
        this.channelInterceptor = channelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getEndpoint())
                .setAllowedOriginPatterns(properties.getAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }
}
