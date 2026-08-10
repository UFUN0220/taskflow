package yvon.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import yvon.backend.auth.AuthTokenResolver;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(NotificationWebSocketProperties.class)
@ConditionalOnProperty(name = {"taskflow.websocket.enabled", "taskflow.auth.enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final NotificationWebSocketProperties properties;
    private final NotificationWebSocketChannelInterceptor channelInterceptor;
    private final AuthTokenResolver tokenResolver;
    private final NotificationWebSocketHandshakeHandler handshakeHandler;

    public NotificationWebSocketConfiguration(NotificationWebSocketProperties properties,
                                               NotificationWebSocketChannelInterceptor channelInterceptor,
                                               AuthTokenResolver tokenResolver,
                                               NotificationWebSocketHandshakeHandler handshakeHandler) {
        this.properties = properties;
        this.channelInterceptor = channelInterceptor;
        this.tokenResolver = tokenResolver;
        this.handshakeHandler = handshakeHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getEndpoint())
                .addInterceptors(new CookieAuthenticationHandshakeInterceptor(tokenResolver))
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns(properties.getAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

}
