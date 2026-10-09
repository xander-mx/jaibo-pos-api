package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.realtime.security.WebSocketIdentityService;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebSocketIdentityServiceIntegrationTest {

    @Autowired WebSocketIdentityService identityService;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private Fixture fixture;

    private record Fixture(
            UUID businessId,
            UUID userId,
            UUID terminalId,
            UUID branchId
    ) {
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void resolvesValidWebSocketIdentity() {
        WebSocketIdentityService.WebSocketIdentity identity =
                identityService.resolve(
                        fixture.userId(),
                        fixture.businessId(),
                        fixture.terminalId()
                );

        assertThat(identity.userId())
                .isEqualTo(fixture.userId());

        assertThat(identity.businessId())
                .isEqualTo(fixture.businessId());

        assertThat(identity.terminalId())
                .isEqualTo(fixture.terminalId());

        assertThat(identity.branchId())
                .isEqualTo(fixture.branchId());
    }

    @Test
    void rejectsUnknownTerminal() {
        UUID unknownTerminalId = UUID.randomUUID();

        assertThrows(
                MessagingException.class,
                () -> identityService.resolve(
                        fixture.userId(),
                        fixture.businessId(),
                        unknownTerminalId
                )
        );
    }

    @Test
    void rejectsTerminalFromAnotherBusiness() {
        Fixture other = createFixture();

        assertThrows(
                MessagingException.class,
                () -> identityService.resolve(
                        fixture.userId(),
                        fixture.businessId(),
                        other.terminalId()
                )
        );
    }

    @Test
    void rejectsInactiveUser() {
        jdbc.update(
                "update app_user set active = false where id = ?",
                fixture.userId()
        );

        assertThrows(
                MessagingException.class,
                () -> identityService.resolve(
                        fixture.userId(),
                        fixture.businessId(),
                        fixture.terminalId()
                )
        );
    }

    @Test
    void rejectsInactiveBranch() {
        jdbc.update(
                "update branch set active = false where id = ?",
                fixture.branchId()
        );

        assertThrows(
                MessagingException.class,
                () -> identityService.resolve(
                        fixture.userId(),
                        fixture.businessId(),
                        fixture.terminalId()
                )
        );
    }

    private Fixture createFixture() {
        return transactions.execute(status -> {
            Business business =
                    businesses.save(
                            new Business(
                                    "Jaibo-" + UUID.randomUUID(),
                                    null,
                                    "MXN",
                                    "America/Mexico_City"
                            )
                    );

            Branch branch =
                    branches.save(
                            new Branch(
                                    business,
                                    "MAIN",
                                    "Principal"
                            )
                    );

            AppUser user =
                    users.save(
                            new AppUser(
                                    business,
                                    "cashier-" + UUID.randomUUID(),
                                    "test-hash",
                                    "Cajero",
                                    UserRole.CASHIER
                            )
                    );

            Terminal terminal =
                    terminals.save(
                            new Terminal(
                                    branch,
                                    user,
                                    "Tablet"
                            )
                    );

            return new Fixture(
                    business.getId(),
                    user.getId(),
                    terminal.getId(),
                    branch.getId()
            );
        });
    }
}