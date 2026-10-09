package com.eljaibo.pos.realtime.security;

import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WebSocketIdentityService {

    private final AppUserRepository users;
    private final TerminalRepository terminals;

    public WebSocketIdentityService(
            AppUserRepository users,
            TerminalRepository terminals
    ) {
        this.users = users;
        this.terminals = terminals;
    }

    @Transactional(readOnly = true)
    public WebSocketIdentity resolve(
            UUID userId,
            UUID businessId,
            UUID terminalId
    ) {
        AppUser user =
                users.findById(userId)
                        .filter(value ->
                                value.isActive()
                                        && value.getBusiness()
                                        .getId()
                                        .equals(businessId)
                                        && value.getBusiness()
                                        .isActive()
                        )
                        .orElseThrow(
                                () -> new MessagingException(
                                        "Active user required"
                                )
                        );

        Terminal terminal =
                terminals
                        .findByIdAndBranchBusinessIdAndActiveTrue(
                                terminalId,
                                businessId
                        )
                        .filter(value ->
                                value.getBranch().isActive()
                        )
                        .orElseThrow(
                                () -> new MessagingException(
                                        "Active terminal required"
                                )
                        );

        return new WebSocketIdentity(
                user.getId(),
                businessId,
                terminal.getId(),
                terminal.getBranch().getId(),
                user.getCredentialVersion()
        );
    }

    public record WebSocketIdentity(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID branchId,
            long credentialVersion
    ) {
        public WebSocketIdentity(UUID userId, UUID businessId, UUID terminalId, UUID branchId) {
            this(userId, businessId, terminalId, branchId, 0);
        }
    }
}
