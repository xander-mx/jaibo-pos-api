package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.api.ChangePasswordRequest;
import com.eljaibo.pos.user.api.ResetPasswordRequest;
import com.eljaibo.pos.user.application.UserService;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserPasswordIntegrationTest {
    private static final String OLD = "Test123!";
    private static final String NEW = "  NuevaClave123!  ";
    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired AppUserRepository users;
    @Autowired UserService service;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder tokenEncoder;
    @Autowired TransactionTemplate transactions;
    private Fixture fixture;
    private record Fixture(UUID business, UUID admin, UUID cashier, UUID foreign,
                           String adminName, String cashierName) { }

    @BeforeEach
    void setUp() {
        fixture = transactions.execute(status -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Business other = businesses.save(new Business("Otro", null, "MXN", "America/Mexico_City"));
            String suffix = UUID.randomUUID().toString();
            String hash = encoder.encode(OLD);
            AppUser admin = users.save(new AppUser(business, "a-" + suffix, hash, "Admin", UserRole.ADMIN));
            AppUser cashier = users.save(new AppUser(business, "c-" + suffix, hash, "Cajero", UserRole.CASHIER));
            AppUser foreign = users.save(new AppUser(other, "f-" + suffix, hash, "Otro", UserRole.CASHIER));
            return new Fixture(business.getId(), admin.getId(), cashier.getId(), foreign.getId(),
                    admin.getUsername(), cashier.getUsername());
        });
    }

    @Test
    void cashierChangesPasswordRevokesJwtAndCanLoginOnlyWithNewPassword() throws Exception {
        String token = login(fixture.cashierName(), OLD);
        change(token, OLD, NEW).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(encoder.matches(NEW, hash(fixture.cashier()))).isTrue();
        assertThat(hash(fixture.cashier())).startsWith("$2").doesNotContain(NEW);
        assertThat(version(fixture.cashier())).isEqualTo(1);
        loginRequest(fixture.cashierName(), OLD).andExpect(status().isUnauthorized());
        protectedRequest(token).andExpect(status().isUnauthorized());
        String fresh = login(fixture.cashierName(), NEW);
        assertThat(((Number) decoder.decode(fresh).getClaim("credential_version")).longValue()).isEqualTo(1);
        protectedRequest(fresh).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordDoesNotMutateCredentialsOrLeakSecrets() throws Exception {
        String token = login(fixture.cashierName(), OLD);
        String hash = hash(fixture.cashier());
        String body = change(token, "Incorrecta123!", NEW).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(hash, NEW, OLD, "Incorrecta123!", "passwordHash");
        assertThat(hash(fixture.cashier())).isEqualTo(hash);
        assertThat(version(fixture.cashier())).isZero();
        protectedRequest(token).andExpect(status().isOk());
    }

    @Test
    void adminResetsCashierButOwnPasswordRequiresCurrentPassword() throws Exception {
        String admin = login(fixture.adminName(), OLD);
        String cashier = login(fixture.cashierName(), OLD);
        reset(admin, fixture.cashier(), NEW).andExpect(status().isNoContent()).andExpect(content().string(""));
        loginRequest(fixture.cashierName(), OLD).andExpect(status().isUnauthorized());
        protectedRequest(cashier).andExpect(status().isUnauthorized());
        protectedRequest(login(fixture.cashierName(), NEW)).andExpect(status().isOk());
        protectedRequest(admin).andExpect(status().isOk());
        reset(admin, fixture.admin(), NEW).andExpect(status().isConflict());
        change(admin, OLD, NEW).andExpect(status().isNoContent());
        protectedRequest(admin).andExpect(status().isUnauthorized());
        protectedRequest(login(fixture.adminName(), NEW)).andExpect(status().isOk());
    }

    @Test
    void resetRequiresAdminAndSameBusinessAndAuthentication() throws Exception {
        String admin = login(fixture.adminName(), OLD);
        String cashier = login(fixture.cashierName(), OLD);
        reset(cashier, fixture.admin(), NEW).andExpect(status().isForbidden());
        reset(admin, fixture.foreign(), NEW).andExpect(status().isNotFound());
        reset(admin, UUID.randomUUID(), NEW).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(json(OLD, NEW))).andExpect(status().isUnauthorized());
        assertThat(version(fixture.foreign())).isZero();
        assertThat(version(fixture.admin())).isZero();
    }

    @Test
    void resetInactiveCashierPreservesInactivityAndOldTokensRemainRevokedAfterReactivation() throws Exception {
        String admin = login(fixture.adminName(), OLD);
        String old = login(fixture.cashierName(), OLD);
        service.deactivate(fixture.admin(), fixture.business(), fixture.cashier());
        reset(admin, fixture.cashier(), NEW).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select active from app_user where id = ?", Boolean.class, fixture.cashier())).isFalse();
        loginRequest(fixture.cashierName(), NEW).andExpect(status().isUnauthorized());
        change(old, OLD, NEW).andExpect(status().isUnauthorized());
        service.activate(fixture.admin(), fixture.business(), fixture.cashier());
        protectedRequest(old).andExpect(status().isUnauthorized());
        protectedRequest(login(fixture.cashierName(), NEW)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "ééééééééééééééééééééééééééééééééééééé"})
    void invalidPasswordsAreRejectedOnBothEndpointsWithoutLeakingValues(String password) throws Exception {
        String cashier = login(fixture.cashierName(), OLD);
        String admin = login(fixture.adminName(), OLD);
        String hash = hash(fixture.cashier());
        String own = change(cashier, OLD, password).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String reset = reset(admin, fixture.cashier(), password).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(own + reset).doesNotContain(hash, OLD, "passwordHash", "password_hash");
        if (password != null && !password.isBlank()) assertThat(own + reset).doesNotContain(password);
        assertThat(hash(fixture.cashier())).isEqualTo(hash);
        assertThat(version(fixture.cashier())).isZero();
    }

    @Test
    void exactBcryptByteLimitAcceptedAndCurrentPasswordValidated() throws Exception {
        String token = login(fixture.cashierName(), OLD);
        change(token, null, NEW).andExpect(status().isBadRequest());
        String boundary = "é".repeat(36);
        change(token, OLD, boundary).andExpect(status().isNoContent());
        protectedRequest(login(fixture.cashierName(), boundary)).andExpect(status().isOk());
    }

    @Test
    void simultaneousOwnChangesHaveExactlyOneWinner() throws Exception {
        String token = login(fixture.cashierName(), OLD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> choices = List.of("GanadoraA123!", "GanadoraB123!");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = choices.stream().map(password -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    service.changePassword(fixture.cashier(), fixture.business(), 0,
                            new ChangePasswordRequest(OLD, password));
                    return 204;
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 401);
        }
        assertThat(version(fixture.cashier())).isEqualTo(1);
        String winning = choices.stream().filter(password -> encoder.matches(password, hash(fixture.cashier()))).findFirst().orElseThrow();
        for (String password : choices) loginRequest(fixture.cashierName(), password)
                .andExpect(password.equals(winning) ? status().isOk() : status().isUnauthorized());
        protectedRequest(token).andExpect(status().isUnauthorized());
    }

    @Test
    void rolledBackChangeDoesNotInvalidateTokenOrPersistNewPassword() throws Exception {
        String token = login(fixture.cashierName(), OLD);
        transactions.executeWithoutResult(status -> {
            service.changePassword(fixture.cashier(), fixture.business(), 0, new ChangePasswordRequest(OLD, NEW));
            status.setRollbackOnly();
        });
        assertThat(version(fixture.cashier())).isZero();
        protectedRequest(token).andExpect(status().isOk());
        login(fixture.cashierName(), OLD);
    }

    @Test
    void loginWaitsForPasswordCommitAndCannotIssueTokenUsingOldPassword() throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        AtomicInteger holder = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> transactions.executeWithoutResult(status -> {
                holder.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                service.changePassword(fixture.cashier(), fixture.business(), 0, new ChangePasswordRequest(OLD, NEW));
                users.flush();
                changed.countDown();
                try {
                    if (!commit.await(15, TimeUnit.SECONDS)) throw new AssertionError("Commit signal missing");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
                var login = executor.submit(() -> loginRequest(fixture.cashierName(), OLD)
                        .andReturn().getResponse().getStatus());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                            "select exists(select 1 from pg_stat_activity a where ? = any(pg_blocking_pids(a.pid)))",
                            Boolean.class, holder.get()));
                    if (blocked || login.isDone()) break;
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                }
                assertThat(blocked).as("Login waits on the password transaction's PostgreSQL lock").isTrue();
                assertThat(login).isNotDone();
                commit.countDown();
                update.get(10, TimeUnit.SECONDS);
                assertThat(login.get(10, TimeUnit.SECONDS)).isEqualTo(401);
            } finally {
                commit.countDown();
            }
        }
        protectedRequest(login(fixture.cashierName(), NEW)).andExpect(status().isOk());
    }

    @Test
    void concurrentAdminResetsPersistOnlyLastCredentialAndIncrementEveryCommittedVersion() throws Exception {
        String old = login(fixture.cashierName(), OLD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> passwords = List.of("ResetA123!", "ResetB123!");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = passwords.stream().map(password -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                service.resetPassword(fixture.admin(), fixture.business(), 0, fixture.cashier(), new ResetPasswordRequest(password));
                return true;
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var task : tasks) assertThat(task.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(version(fixture.cashier())).isEqualTo(2);
        String hash = hash(fixture.cashier());
        assertThat(passwords.stream().filter(password -> encoder.matches(password, hash)).count()).isEqualTo(1);
        for (String password : passwords) loginRequest(fixture.cashierName(), password)
                .andExpect(encoder.matches(password, hash) ? status().isOk() : status().isUnauthorized());
        protectedRequest(old).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyJwtWithoutVersionWorksOnlyUntilFirstPasswordChange() throws Exception {
        String original = login(fixture.cashierName(), OLD);
        JwtClaimsSet claims = JwtClaimsSet.builder().claims(values -> {
            values.putAll(decoder.decode(original).getClaims());
            values.remove("credential_version");
        }).build();
        String legacy = tokenEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        protectedRequest(legacy).andExpect(status().isOk());
        change(legacy, OLD, NEW).andExpect(status().isNoContent());
        protectedRequest(legacy).andExpect(status().isUnauthorized());
        protectedRequest(login(fixture.cashierName(), NEW)).andExpect(status().isOk());
    }

    private ResultActions change(String token, String current, String password) throws Exception {
        return mvc.perform(patch("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(current, password)));
    }
    private ResultActions reset(String token, UUID target, String password) throws Exception {
        return mvc.perform(patch("/api/v1/users/{userId}/password", target).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":" + quoted(password) + "}"));
    }
    private ResultActions protectedRequest(String token) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
    private ResultActions loginRequest(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":" + quoted(username) + ",\"password\":" + quoted(password) + "}"));
    }
    private String login(String username, String password) throws Exception {
        String body = loginRequest(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash", "password_hash", hash(username.equals(fixture.adminName()) ? fixture.admin() : fixture.cashier()));
        return JsonPath.read(body, "$.accessToken");
    }
    private String json(String current, String password) {
        return "{\"currentPassword\":" + quoted(current) + ",\"newPassword\":" + quoted(password) + "}";
    }
    private String quoted(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private String hash(UUID user) { return jdbc.queryForObject("select password_hash from app_user where id = ?", String.class, user); }
    private long version(UUID user) { return jdbc.queryForObject("select credential_version from app_user where id = ?", Long.class, user); }
}
