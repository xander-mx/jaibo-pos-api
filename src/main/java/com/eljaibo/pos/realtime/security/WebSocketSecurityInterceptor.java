package com.eljaibo.pos.realtime.security;

import org.slf4j.Logger;
import com.eljaibo.pos.auth.security.CredentialVersion;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class WebSocketSecurityInterceptor
        implements ChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    WebSocketSecurityInterceptor.class
            );

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final WebSocketIdentityService identityService;
    private final WebSocketSessionRegistry sessions;

    public WebSocketSecurityInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            WebSocketIdentityService identityService,
            WebSocketSessionRegistry sessions
    ) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter =
                jwtAuthenticationConverter;
        this.identityService = identityService;
        this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnection(accessor);
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            // This application publishes server events; it has no client SEND handlers.
            throw new MessagingException("Client SEND is not allowed");
        }

        return message;
    }

    private void authenticateConnection(
            StompHeaderAccessor accessor
    ) {
        String authorization =
                accessor.getFirstNativeHeader(
                        HttpHeaders.AUTHORIZATION
                );

        if (
                authorization == null ||
                        !authorization.startsWith("Bearer ")
        ) {
            throw new MessagingException(
                    "Authorization bearer token is required"
            );
        }

        String terminalHeader =
                accessor.getFirstNativeHeader(
                        "X-Terminal-Id"
                );

        if (
                terminalHeader == null ||
                        terminalHeader.isBlank()
        ) {
            throw new MessagingException(
                    "X-Terminal-Id is required"
            );
        }

        try {
            Jwt jwt =
                    jwtDecoder.decode(
                            authorization.substring(7)
                    );

            UUID userId =
                    UUID.fromString(
                            jwt.getClaimAsString("user_id")
                    );

            UUID businessId =
                    UUID.fromString(
                            jwt.getClaimAsString("business_id")
                    );

            UUID terminalId =
                    UUID.fromString(terminalHeader);

            WebSocketIdentityService.WebSocketIdentity identity =
                    identityService.resolve(
                            userId,
                            businessId,
                            terminalId
                    );
            if (identity.credentialVersion() != CredentialVersion.from(jwt)) {
                throw new MessagingException("Invalid credentials");
            }
            sessions.authenticate(accessor.getSessionId(), identity);
            Objects.requireNonNull(accessor.getSessionAttributes()).put("credentialVersion", identity.credentialVersion());

            accessor.setUser(
                    jwtAuthenticationConverter.convert(jwt)
            );

            Objects.requireNonNull(
                    accessor.getSessionAttributes()
            ).put(
                    "userId",
                    identity.userId()
            );

            accessor.getSessionAttributes().put(
                    "businessId",
                    identity.businessId()
            );

            accessor.getSessionAttributes().put(
                    "terminalId",
                    identity.terminalId()
            );

            accessor.getSessionAttributes().put(
                    "branchId",
                    identity.branchId()
            );

            log.info(
                    "WebSocket authenticated userId={} businessId={} terminalId={} branchId={}",
                    identity.userId(),
                    identity.businessId(),
                    identity.terminalId(),
                    identity.branchId()
            );

        } catch (JwtException ex) {
            throw new MessagingException(
                    "Invalid JWT",
                    ex
            );

        } catch (IllegalArgumentException ex) {
            throw new MessagingException(
                    "Invalid WebSocket identity",
                    ex
            );
        }
    }

    private void authorizeSubscription(
            StompHeaderAccessor accessor
    ) {
        if (!sessions.isAuthorized(accessor.getSessionId())) {
            throw new MessagingException("Authenticated WebSocket session required");
        }
        var attributes = Objects.requireNonNull(accessor.getSessionAttributes());
        WebSocketIdentityService.WebSocketIdentity current = identityService.resolve(
                (UUID) attributes.get("userId"), (UUID) attributes.get("businessId"),
                (UUID) attributes.get("terminalId"));
        if (!Long.valueOf(current.credentialVersion()).equals(attributes.getOrDefault("credentialVersion", 0L))) {
            throw new MessagingException("Invalid credentials");
        }
        Object branchValue =
                Objects.requireNonNull(
                        accessor.getSessionAttributes()
                ).get("branchId");

        if (!(branchValue instanceof UUID branchId)) {
            throw new MessagingException(
                    "Authenticated WebSocket session required"
            );
        }
        if (!current.branchId().equals(branchId)) throw new MessagingException("Subscription not allowed");

        String destination =
                accessor.getDestination();

        String allowedDestination =
                "/topic/branches/"
                        + branchId
                        + "/sales";

        if (!allowedDestination.equals(destination)) {
            log.warn(
                    "Rejected WebSocket subscription destination={} branchId={}",
                    destination,
                    branchId
            );

            throw new MessagingException(
                    "Subscription not allowed"
            );
        }
    }
}
