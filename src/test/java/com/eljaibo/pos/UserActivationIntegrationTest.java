package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.api.UserResponse;
import com.eljaibo.pos.user.application.UserService;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class UserActivationIntegrationTest {
    private static final String PASSWORD = "Test123!";
    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired UserService service;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private Fixture fixture;
    private record Fixture(UUID business, UUID admin, UUID secondAdmin, UUID cashier, UUID foreignUser,
                           UUID session, UUID terminal, String adminName, String secondName, String cashierName) { }

    @BeforeEach
    void setUp() {
        fixture = transactions.execute(status -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Business other = businesses.save(new Business("Otro", null, "MXN", "America/Mexico_City"));
            String suffix = UUID.randomUUID().toString();
            String hash = encoder.encode(PASSWORD);
            AppUser admin = users.save(new AppUser(business, "a-" + suffix, hash, "Admin", UserRole.ADMIN));
            AppUser second = users.save(new AppUser(business, "b-" + suffix, hash, "Segundo", UserRole.ADMIN));
            AppUser cashier = users.save(new AppUser(business, "c-" + suffix, hash, "Cajero", UserRole.CASHIER));
            AppUser foreign = users.save(new AppUser(other, "f-" + suffix, hash, "Otro", UserRole.CASHIER));
            Branch branch = branches.save(new Branch(business, "MAIN", "Principal"));
            Terminal terminal = terminals.save(new Terminal(branch, cashier, "Tablet"));
            OperatingCycle cycle = cycles.save(OperatingCycle.open(branch, LocalDate.of(2026, 10, 2)));
            CashSession session = sessions.save(new CashSession(cycle, LocalDate.of(2026, 10, 4),
                    new BigDecimal("500.00"), cashier, terminal));
            return new Fixture(business.getId(), admin.getId(), second.getId(), cashier.getId(), foreign.getId(),
                    session.getId(), terminal.getId(), admin.getUsername(), second.getUsername(), cashier.getUsername());
        });
    }

    @Test
    void disablesCashierRevokesOldJwtAndReactivatesWithoutLosingHistory(CapturedOutput output) throws Exception {
        String adminToken = login(fixture.adminName());
        String cashierToken = login(fixture.cashierName());
        mvc.perform(get("/api/v1/units").header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                .andExpect(status().isOk());
        String oldHash = jdbc.queryForObject("select password_hash from app_user where id = ?",
                String.class, fixture.cashier());
        change(adminToken, fixture.cashier(), "deactivate", false);
        mvc.perform(loginRequest(fixture.cashierName())).andExpect(status().isUnauthorized());
        for (String path : List.of("/api/v1/units", "/api/v1/auth/me", "/api/v1/customers")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                    .andExpect(status().isUnauthorized());
        }
        change(adminToken, fixture.cashier(), "activate", true);
        String freshToken = login(fixture.cashierName());
        mvc.perform(get("/api/v1/units").header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select password_hash from app_user where id = ?",
                String.class, fixture.cashier())).isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("select opened_by_user_id from cash_session where id = ?",
                UUID.class, fixture.session())).isEqualTo(fixture.cashier());
        assertThat(jdbc.queryForObject("select registered_by_user_id from terminal where id = ?",
                UUID.class, fixture.terminal())).isEqualTo(fixture.cashier());
        assertThat(output).contains("administratorId=" + fixture.admin(), "userId=" + fixture.cashier(),
                "active=false", "active=true");
    }

    @Test
    void administratorCannotDeactivateSelf() throws Exception {
        mvc.perform(patch("/api/v1/users/{id}/deactivate", fixture.admin())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(fixture.adminName())))
                .andExpect(status().isConflict());
        assertThat(isActive(fixture.admin())).isTrue();
        assertThat(activeAdmins()).isEqualTo(2);
    }

    @Test
    void lastActiveAdministratorCannotBeDeactivated() throws Exception {
        String adminToken = login(fixture.adminName());
        String secondToken = login(fixture.secondName());
        change(adminToken, fixture.secondAdmin(), "deactivate", false);
        assertThat(activeAdmins()).isEqualTo(1);
        // The sole remaining admin is also protected by the self-deactivation rule.
        mvc.perform(patch("/api/v1/users/{id}/deactivate", fixture.admin())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/users/{id}/deactivate", fixture.admin())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test
    void rejectsForeignAndUnknownTargets() throws Exception {
        String token = login(fixture.adminName());
        for (String operation : List.of("activate", "deactivate")) {
            for (UUID target : List.of(fixture.foreignUser(), UUID.randomUUID())) {
                mvc.perform(patch("/api/v1/users/{id}/" + operation, target)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isNotFound());
            }
        }
        assertThat(isActive(fixture.foreignUser())).isTrue();
    }

    @Test
    void cashierAndAnonymousCannotChangeActivation() throws Exception {
        String cashierToken = login(fixture.cashierName());
        for (String operation : List.of("activate", "deactivate")) {
            mvc.perform(patch("/api/v1/users/{id}/" + operation, fixture.secondAdmin())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/users/{id}/" + operation, fixture.secondAdmin()))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(isActive(fixture.secondAdmin())).isTrue();
    }

    @Test
    void operationsAreIdempotent() throws Exception {
        String token = login(fixture.adminName());
        change(token, fixture.cashier(), "deactivate", false);
        var disabledAt = jdbc.queryForObject("select updated_at from app_user where id = ?",
                java.time.OffsetDateTime.class, fixture.cashier());
        change(token, fixture.cashier(), "deactivate", false);
        assertThat(jdbc.queryForObject("select updated_at from app_user where id = ?",
                java.time.OffsetDateTime.class, fixture.cashier())).isEqualTo(disabledAt);
        change(token, fixture.cashier(), "activate", true);
        var activatedAt = jdbc.queryForObject("select updated_at from app_user where id = ?",
                java.time.OffsetDateTime.class, fixture.cashier());
        change(token, fixture.cashier(), "activate", true);
        assertThat(jdbc.queryForObject("select updated_at from app_user where id = ?",
                java.time.OffsetDateTime.class, fixture.cashier())).isEqualTo(activatedAt);
    }

    @Test
    void concurrentAdministratorsCannotLeaveBusinessWithoutActiveAdmin() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID actor = i == 0 ? fixture.admin() : fixture.secondAdmin();
                UUID target = i == 0 ? fixture.secondAdmin() : fixture.admin();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    try {
                        // Service proxies start independent transactions on each executor thread.
                        return service.deactivate(actor, fixture.business(), target);
                    } catch (ResponseStatusException exception) {
                        return exception;
                    }
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get(15, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(UserResponse.class::isInstance).count()).isEqualTo(1);
            var rejected = outcomes.stream().filter(ResponseStatusException.class::isInstance)
                    .map(ResponseStatusException.class::cast).toList();
            assertThat(rejected).hasSize(1);
            assertThat(rejected.getFirst().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(activeAdmins()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user where business_id = ?",
                Integer.class, fixture.business())).isEqualTo(3);
    }

    @Test
    void publicRoutesRemainAvailableAndWebSocketHandshakeDoesNotRequireHttpJwt() throws Exception {
        String oldToken = login(fixture.cashierName());
        service.deactivate(fixture.admin(), fixture.business(), fixture.cashier());
        mvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(loginRequest(fixture.adminName()).header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk());
        // HTTP access is public; an upgrade-less request is rejected by the WebSocket handler, not security.
        mvc.perform(get("/ws")).andExpect(status().isBadRequest());
    }

    @Test
    void centralIdentityValidationUsesExactlyOneDatabaseQuery() throws Exception {
        String token = login(fixture.cashierName());
        String adminToken = login(fixture.adminName());
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            // /me uses only JWT claims: the one SQL statement is the centralized identity projection.
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(fixture.cashier().toString()));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            statistics.clear();
            mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
            // One identity projection plus one scoped user list, with no duplicate administrator lookup.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private void change(String token, UUID target, String operation, boolean active) throws Exception {
        mvc.perform(patch("/api/v1/users/{id}/" + operation, target)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target.toString()))
                .andExpect(jsonPath("$.active").value(active))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String name) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private String login(String name) throws Exception {
        String response = mvc.perform(loginRequest(name)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private boolean isActive(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select active from app_user where id = ?", Boolean.class, user));
    }

    private int activeAdmins() {
        return jdbc.queryForObject("select count(*) from app_user where business_id = ? and active and role = 'ADMIN'",
                Integer.class, fixture.business());
    }
}
