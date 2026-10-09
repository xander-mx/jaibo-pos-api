package com.eljaibo.pos.realtime.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSecurityInterceptorTest {

    private JwtDecoder jwtDecoder;
    private JwtAuthenticationConverter authenticationConverter;
    private WebSocketIdentityService identityService;
    private MessageChannel channel;

    private WebSocketSecurityInterceptor interceptor;
    private WebSocketSessionRegistry sessions;

    private UUID userId;
    private UUID businessId;
    private UUID terminalId;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);

        authenticationConverter =
                mock(JwtAuthenticationConverter.class);

        identityService =
                mock(WebSocketIdentityService.class);

        channel = mock(MessageChannel.class);
        sessions = mock(WebSocketSessionRegistry.class);
        when(sessions.isAuthorized(any(String.class))).thenReturn(true);

        interceptor =
                new WebSocketSecurityInterceptor(
                        jwtDecoder,
                        authenticationConverter,
                        identityService,
                        sessions
                );

        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        terminalId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        when(identityService.resolve(userId, businessId, terminalId)).thenReturn(
                new WebSocketIdentityService.WebSocketIdentity(userId, businessId, terminalId, branchId));
    }

    @Test
    void rejectsConnectWithoutAuthorization() {
        Message<byte[]> message =
                connectMessage(
                        null,
                        terminalId.toString()
                );

        MessagingException exception =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(
                                message,
                                channel
                        )
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Authorization bearer token is required"
                );
    }

    @Test
    void rejectsConnectWithoutTerminal() {
        Message<byte[]> message =
                connectMessage(
                        "Bearer valid-token",
                        null
                );

        MessagingException exception =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(
                                message,
                                channel
                        )
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        "X-Terminal-Id is required"
                );
    }

    @Test
    void acceptsValidConnect() {
        Jwt jwt = jwt();

        when(
                jwtDecoder.decode("valid-token")
        ).thenReturn(jwt);

        when(
                identityService.resolve(
                        userId,
                        businessId,
                        terminalId
                )
        ).thenReturn(
                new WebSocketIdentityService.WebSocketIdentity(
                        userId,
                        businessId,
                        terminalId,
                        branchId
                )
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        when(
                authenticationConverter.convert(jwt)
        ).thenReturn(authentication);

        Message<byte[]> message =
                connectMessage(
                        "Bearer valid-token",
                        terminalId.toString()
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        channel
                );

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(result);

        assertThat(accessor.getUser())
                .isEqualTo(authentication);

        assertThat(
                accessor.getSessionAttributes()
        ).containsEntry(
                "userId",
                userId
        ).containsEntry(
                "businessId",
                businessId
        ).containsEntry(
                "terminalId",
                terminalId
        ).containsEntry(
                "branchId",
                branchId
        );
    }

    @Test
    void allowsSubscriptionToOwnBranch() {
        Message<byte[]> message =
                subscribeMessage(
                        branchId,
                        "/topic/branches/"
                                + branchId
                                + "/sales"
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        channel
                );

        assertThat(result)
                .isSameAs(message);
    }

    @Test
    void rejectsSubscriptionToAnotherBranch() {
        UUID anotherBranchId =
                UUID.randomUUID();

        Message<byte[]> message =
                subscribeMessage(
                        branchId,
                        "/topic/branches/"
                                + anotherBranchId
                                + "/sales"
                );

        MessagingException exception =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(
                                message,
                                channel
                        )
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Subscription not allowed"
                );
    }

    private Message<byte[]> connectMessage(
            String authorization,
            String terminal
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setSessionAttributes(
                new HashMap<>()
        );
        accessor.setSessionId("test-session");

        if (authorization != null) {
            accessor.setNativeHeader(
                    "Authorization",
                    authorization
            );
        }

        if (terminal != null) {
            accessor.setNativeHeader(
                    "X-Terminal-Id",
                    terminal
            );
        }

        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private Message<byte[]> subscribeMessage(
            UUID authenticatedBranchId,
            String destination
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.SUBSCRIBE
                );

        Map<String, Object> sessionAttributes =
                new HashMap<>();

        sessionAttributes.put(
                "branchId",
                authenticatedBranchId
        );

        accessor.setSessionAttributes(
                sessionAttributes
        );
        accessor.setSessionId("test-session");
        sessionAttributes.put("userId", userId);
        sessionAttributes.put("businessId", businessId);
        sessionAttributes.put("terminalId", terminalId);

        accessor.setDestination(
                destination
        );

        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private Jwt jwt() {
        Instant now = Instant.now();

        return Jwt.withTokenValue(
                        "valid-token"
                )
                .header(
                        "alg",
                        "none"
                )
                .subject("cashier")
                .issuedAt(now)
                .expiresAt(
                        now.plusSeconds(3600)
                )
                .claim(
                        "user_id",
                        userId.toString()
                )
                .claim(
                        "business_id",
                        businessId.toString()
                )
                .claim(
                        "roles",
                        List.of("CASHIER")
                )
                .build();
    }
}
