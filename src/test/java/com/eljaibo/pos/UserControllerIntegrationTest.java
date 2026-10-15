package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.api.CreateUserRequest;
import com.eljaibo.pos.user.api.UserResponse;
import com.eljaibo.pos.user.application.UserService;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserControllerIntegrationTest {

    private static final String PASSWORD = "  Password123!  ";

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transactions;
    @Autowired UserService service;
    @Autowired JdbcTemplate jdbc;

    private Fixture fixture;

    private record Fixture(UUID business, UUID admin, UUID cashier, UUID otherBusiness, UUID otherAdmin,
                           String adminUsername, String cashierUsername, String otherUsername) { }

    @BeforeEach
    void setUp() {
        // Committed data is also visible to concurrent registration transactions.
        fixture = transactions.execute(status -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Business other = businesses.save(new Business("Otro negocio", null, "MXN", "America/Mexico_City"));
            String suffix = UUID.randomUUID().toString();
            String hash = passwordEncoder.encode(PASSWORD);
            AppUser admin = users.save(new AppUser(business, "admin-" + suffix, hash, "Administrador", UserRole.ADMIN));
            AppUser cashier = users.save(new AppUser(business, "cashier-" + suffix, hash, "Cajero", UserRole.CASHIER));
            AppUser otherAdmin = users.save(new AppUser(other, "other-" + suffix, hash, "Otro admin", UserRole.ADMIN));
            return new Fixture(business.getId(), admin.getId(), cashier.getId(), other.getId(), otherAdmin.getId(),
                    admin.getUsername(), cashier.getUsername(), otherAdmin.getUsername());
        });
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void createsActiveUserWithBcryptAndExistingLogin(UserRole role) throws Exception {
        String username = "new-" + UUID.randomUUID();
        String body = """
                {"username":" %s ","password":"%s","displayName":" Nuevo usuario ","role":"%s"}
                """.formatted(username.toUpperCase(), PASSWORD, role);
        String response = mvc.perform(authenticated(post("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value("Nuevo usuario"))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        var row = jdbc.queryForMap("select * from app_user where id = ?", id);
        assertThat(row.get("business_id")).isEqualTo(fixture.business());
        assertThat(row.get("active")).isEqualTo(true);
        String hash = (String) row.get("password_hash");
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(hash).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD.trim(), hash)).isFalse();
        assertThat(response).doesNotContain(hash, PASSWORD);

        String token = login(username.toUpperCase());
        var list = mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        if (role == UserRole.ADMIN) {
            list.andExpect(status().isOk());
        } else {
            list.andExpect(status().isForbidden());
        }
    }

    @Test
    void listsOnlyOwnBusinessIncludingInactiveUsersWithoutCredentials() throws Exception {
        transactions.executeWithoutResult(status -> users.findById(fixture.cashier()).orElseThrow().deactivate());
        String response = mvc.perform(authenticated(get("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(fixture.admin().toString()))
                .andExpect(jsonPath("$[1].id").value(fixture.cashier().toString()))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$..password").isEmpty())
                .andExpect(jsonPath("$..passwordHash").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(fixture.otherAdmin().toString(), fixture.otherUsername(), PASSWORD, "$2a$");
        mvc.perform(authenticated(get("/api/v1/users"), fixture.otherAdmin(), fixture.otherBusiness(), UserRole.ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(fixture.otherAdmin().toString()));
    }

    @Test
    void cashierCannotListOrCreateUsers() throws Exception {
        String token = login(fixture.cashierUsername());
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest("unauthorized")))
                .andExpect(status().isForbidden());
        assertThat(ownUserCount()).isEqualTo(2);
    }

    @Test
    void anonymousCannotListOrCreateUsers() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("anonymous")))
                .andExpect(status().isUnauthorized());
        assertThat(ownUserCount()).isEqualTo(2);
    }

    @Test
    void rejectsBusinessClaimInconsistentWithAuthenticatedUser() throws Exception {
        assertRejectedIdentity(fixture.admin(), fixture.otherBusiness(), 401);
        assertThat(jdbc.queryForObject("select count(*) from app_user where business_id = ?",
                Integer.class, fixture.otherBusiness())).isEqualTo(1);
    }

    @Test
    void rejectsInactiveAdministratorDespiteAdminJwt() throws Exception {
        transactions.executeWithoutResult(status -> users.findById(fixture.admin()).orElseThrow().deactivate());
        assertRejectedIdentity(fixture.admin(), fixture.business(), 401);
    }

    @Test
    void checksPersistedRoleEvenWhenJwtClaimsAdmin() throws Exception {
        assertRejectedIdentity(fixture.cashier(), fixture.business(), 403);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsCaseInsensitiveDuplicateUsernameAcrossBusinesses(boolean sameBusiness) throws Exception {
        String existing = sameBusiness ? fixture.cashierUsername() : fixture.otherUsername();
        mvc.perform(authenticated(post("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(" " + existing.toUpperCase() + " ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username already exists"));
        assertThat(ownUserCount()).isEqualTo(2);
    }

    @Test
    void concurrentRegistrationOfSameUsernameCreatesExactlyOneUser() throws Exception {
        String username = "race-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String candidate = i == 0 ? username : username.toUpperCase();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    try {
                        return service.create(fixture.admin(), fixture.business(),
                                new CreateUserRequest(candidate, PASSWORD, "Concurrent user", UserRole.CASHIER));
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
            var failures = outcomes.stream().filter(ResponseStatusException.class::isInstance)
                    .map(ResponseStatusException.class::cast).toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(jdbc.queryForObject("select count(*) from app_user where lower(username) = ?",
                Integer.class, username)).isEqualTo(1);
        assertThat(ownUserCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"username\":\" \",\"password\":\"p\",\"displayName\":\"Name\",\"role\":\"ADMIN\"}",
            "{\"username\":\"new\",\"password\":\" \",\"displayName\":\"Name\",\"role\":\"ADMIN\"}",
            "{\"username\":\"new\",\"password\":\"p\",\"displayName\":\" \",\"role\":\"ADMIN\"}",
            "{\"username\":\"new\",\"password\":\"p\",\"displayName\":\"Name\",\"role\":null}",
            "{\"username\":\"new\",\"password\":\"p\",\"displayName\":\"Name\",\"role\":\"OWNER\"}"
    })
    void rejectsInvalidRequests(String body) throws Exception {
        mvc.perform(authenticated(post("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(ownUserCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "displayName", "password", "utf8Password"})
    void rejectsOversizedFieldsAndBcryptByteOverflow(String field) throws Exception {
        String username = field.equals("username") ? "u".repeat(61) : "new-" + UUID.randomUUID();
        String name = field.equals("displayName") ? "n".repeat(121) : "Name";
        String password = switch (field) {
            case "password" -> "p".repeat(73);
            case "utf8Password" -> "é".repeat(37);
            default -> PASSWORD;
        };
        String body = """
                {"username":"%s","password":"%s","displayName":"%s","role":"ADMIN"}
                """.formatted(username, password, name);
        String response = mvc.perform(authenticated(post("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(password);
        assertThat(ownUserCount()).isEqualTo(2);
    }

    @Test
    void registrationCannotChooseAnotherBusinessOrCreateInactiveUser() throws Exception {
        String username = "scoped-" + UUID.randomUUID();
        String body = """
                {"username":"%s","password":"%s","displayName":"Name","role":"CASHIER",
                 "businessId":"%s","active":false}
                """.formatted(username, PASSWORD, fixture.otherBusiness());
        String response = mvc.perform(authenticated(post("/api/v1/users"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat(jdbc.queryForObject("select business_id from app_user where id = ?", UUID.class, id))
                .isEqualTo(fixture.business());
    }

    @Test
    void rawUserRepositoryIsNotExposedThroughDataRest() throws Exception {
        mvc.perform(authenticated(get("/appUsers"), fixture.admin(), fixture.business(), UserRole.ADMIN))
                .andExpect(status().isNotFound());
        mvc.perform(authenticated(post("/appUsers"), fixture.admin(), fixture.business(), UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest("raw")))
                .andExpect(status().isNotFound());
        mvc.perform(authenticated(get("/"), fixture.admin(), fixture.business(), UserRole.ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$._links.appUsers").doesNotExist());
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private void assertRejectedIdentity(UUID user, UUID business, int expectedStatus) throws Exception {
        mvc.perform(authenticated(get("/api/v1/users"), user, business, UserRole.ADMIN))
                .andExpect(status().is(expectedStatus));
        mvc.perform(authenticated(post("/api/v1/users"), user, business, UserRole.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest("forbidden")))
                .andExpect(status().is(expectedStatus));
        assertThat(ownUserCount()).isEqualTo(2);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request,
                                                        UUID user, UUID business, UserRole role) {
        return request.with(jwt().jwt(token -> token.subject("user")
                        .claim("user_id", user.toString()).claim("business_id", business.toString())
                        .claim("roles", List.of(role.name())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String validRequest(String username) {
        return """
                {"username":"%s","password":"%s","displayName":"Nuevo usuario","role":"CASHIER"}
                """.formatted(username, PASSWORD);
    }

    private int ownUserCount() {
        return jdbc.queryForObject("select count(*) from app_user where business_id = ?",
                Integer.class, fixture.business());
    }
}
