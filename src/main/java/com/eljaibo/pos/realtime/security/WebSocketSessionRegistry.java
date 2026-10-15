package com.eljaibo.pos.realtime.security;

import com.eljaibo.pos.user.domain.UserDeactivated;
import com.eljaibo.pos.user.domain.UserCredentialsChanged;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WebSocketSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AppUserRepository users;

    private static final class Session {
        private final WebSocketSession socket;
        private volatile WebSocketIdentityService.WebSocketIdentity identity;
        private volatile boolean revoked;

        private Session(WebSocketSession socket) { this.socket = socket; }
    }

    public WebSocketSessionRegistry(AppUserRepository users) { this.users = users; }

    public void connected(WebSocketSession socket) {
        sessions.put(socket.getId(), new Session(socket));
    }

    public void disconnected(String sessionId) { sessions.remove(sessionId); }

    public void authenticate(String sessionId, WebSocketIdentityService.WebSocketIdentity identity) {
        Session session = sessions.get(sessionId);
        if (session == null || session.revoked) throw new MessagingException("WebSocket session is closed");
        session.identity = identity;
    }

    public boolean isAuthorized(String sessionId) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        return session != null && session.identity != null && !session.revoked;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void userDeactivated(UserDeactivated event) {
        sessions.values().stream().filter(session -> session.identity != null
                        && session.identity.userId().equals(event.userId())
                        && session.identity.businessId().equals(event.businessId()))
                .forEach(this::revoke);
        log.info("WebSocket sessions revoked: administratorId={}, userId={}, businessId={}",
                event.administratorId(), event.userId(), event.businessId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void credentialsChanged(UserCredentialsChanged event) {
        sessions.values().stream().filter(session -> session.identity != null
                        && session.identity.userId().equals(event.userId())
                        && session.identity.businessId().equals(event.businessId())
                        && session.identity.credentialVersion() < event.credentialVersion())
                .forEach(this::revoke);
        log.info("WebSocket credentials revoked: actorId={}, userId={}, businessId={}, credentialVersion={}",
                event.actorId(), event.userId(), event.businessId(), event.credentialVersion());
    }

    // One grouped query per instance, not one query per session or STOMP frame.
    @Scheduled(fixedDelayString = "${app.security.websocket.recheck-interval-ms:1000}")
    public void recheckConnectedUsers() { recheck(null); }

    public void recheckBranch(UUID branchId) { recheck(branchId); }

    private void recheck(UUID branchId) {
        List<Session> candidates = sessions.values().stream()
                .filter(session -> session.identity != null && !session.revoked
                        && (branchId == null || branchId.equals(session.identity.branchId())))
                .toList();
        if (candidates.isEmpty()) return;
        try {
            var active = users.findActiveIdentities(candidates.stream()
                            .map(session -> session.identity.userId()).distinct().toList())
                    .stream().collect(Collectors.toMap(AppUserRepository.ActiveIdentity::getId, Function.identity()));
            for (Session session : candidates) {
                var identity = active.get(session.identity.userId());
                if (identity == null || !identity.getBusinessId().equals(session.identity.businessId())
                        || identity.getCredentialVersion() != session.identity.credentialVersion()) revoke(session);
            }
        } catch (DataAccessException exception) {
            // Without a reliable state check, fail closed rather than deliver possibly revoked data.
            log.error("Unable to validate WebSocket users; closing affected sessions", exception);
            candidates.forEach(this::revoke);
        }
    }

    private void revoke(Session session) {
        session.revoked = true; // Outbound frames are suppressed even if closing the transport fails.
        try {
            if (session.socket.isOpen()) session.socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            log.warn("Unable to close revoked WebSocket sessionId={}", session.socket.getId(), exception);
        } finally {
            sessions.remove(session.socket.getId(), session);
        }
    }
}
