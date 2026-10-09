package com.eljaibo.pos.realtime.config;

import com.eljaibo.pos.realtime.security.WebSocketSecurityInterceptor;
import com.eljaibo.pos.realtime.security.WebSocketSessionRegistry;
import com.eljaibo.pos.realtime.security.WebSocketDeliveryInterceptor;
import com.eljaibo.pos.realtime.security.WebSocketPublicationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor securityInterceptor;
    private final WebSocketSessionRegistry sessions;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            WebSocketSecurityInterceptor securityInterceptor,
            WebSocketSessionRegistry sessions,
            @Value("${app.frontend.allowed-origins:http://localhost:4200}")
            String allowedOrigins
    ) {
        this.securityInterceptor = securityInterceptor;
        this.sessions = sessions;
        this.allowedOrigins =
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker(
                "/topic"
        );

        registry.setApplicationDestinationPrefixes(
                "/app"
        );
        registry.configureBrokerChannel().interceptors(new WebSocketPublicationInterceptor(sessions));
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(
                        allowedOrigins.toArray(String[]::new)
                );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                securityInterceptor
        );
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketDeliveryInterceptor(sessions));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.connected(session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessions.disconnected(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.disconnected(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
