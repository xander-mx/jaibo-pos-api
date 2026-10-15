package com.eljaibo.pos;

import com.eljaibo.pos.auth.api.LoginRequest;
import com.eljaibo.pos.auth.application.AuthService;
import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.realtime.sale.SaleRealtimePublisher;
import com.eljaibo.pos.realtime.security.WebSocketSessionRegistry;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.application.UserService;
import com.eljaibo.pos.user.api.ChangePasswordRequest;
import com.eljaibo.pos.user.api.ResetPasswordRequest;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.websocket.recheck-interval-ms=3600000")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebSocketRevocationIntegrationTest {
    private static final String PASSWORD = "Test123!";
    @Value("${local.server.port}") int port;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired UserService userService;
    @Autowired SaleRealtimePublisher publisher;
    @Autowired TransactionTemplate transactions;
    @Autowired WebSocketSessionRegistry sessions;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Fixture fixture;
    private WebSocketStompClient stomp;
    private final List<Client> connected = new ArrayList<>();

    private record Fixture(UUID business, UUID branch, UUID admin, UUID cashier, UUID activeUser,
                           UUID terminal, String cashierName, String activeName, UUID otherBranch,
                           UUID otherTerminal, String otherName) { }

    private static final class Client {
        private StompSession session;
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        private void event(UUID saleId) throws InterruptedException {
            assertThat(received.poll(10, TimeUnit.SECONDS)).contains(saleId.toString());
        }
    }

    @BeforeEach
    void setUp() {
        fixture = transactions.execute(status -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Business other = businesses.save(new Business("Otro", null, "MXN", "America/Mexico_City"));
            Branch branch = branches.save(new Branch(business, "MAIN", "Principal"));
            Branch foreign = branches.save(new Branch(other, "MAIN", "Otra sucursal"));
            String suffix = UUID.randomUUID().toString();
            String hash = encoder.encode(PASSWORD);
            AppUser admin = users.save(new AppUser(business, "a-" + suffix, hash, "Admin", UserRole.ADMIN));
            AppUser cashier = users.save(new AppUser(business, "c-" + suffix, hash, "Cajero", UserRole.CASHIER));
            AppUser active = users.save(new AppUser(business, "b-" + suffix, hash, "Activo", UserRole.CASHIER));
            AppUser otherUser = users.save(new AppUser(other, "f-" + suffix, hash, "Otro", UserRole.CASHIER));
            Terminal terminal = terminals.save(new Terminal(branch, admin, "Tablet"));
            Terminal otherTerminal = terminals.save(new Terminal(foreign, otherUser, "Tablet externa"));
            return new Fixture(business.getId(), branch.getId(), admin.getId(), cashier.getId(), active.getId(),
                    terminal.getId(), cashier.getUsername(), active.getUsername(), foreign.getId(),
                    otherTerminal.getId(), otherUser.getUsername());
        });
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        // Receive server JSON as text without depending on a particular Jackson client converter.
        stomp.setMessageConverter(new StringMessageConverter() {
            @Override protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) { return true; }
        });
    }

    @AfterEach
    void cleanUp() {
        for (Client client : connected) {
            if (client.session.isConnected()) client.session.disconnect();
        }
        if (stomp != null) stomp.stop();
        connected.clear();
    }

    @Test
    void deactivationClosesAllUserSessionsAndActiveUserKeepsReceivingEvents() throws Exception {
        Client first = connect(fixture.cashierName(), fixture.terminal());
        Client second = connect(fixture.cashierName(), fixture.terminal());
        Client active = connect(fixture.activeName(), fixture.terminal());
        subscribe(first, fixture.branch());
        subscribe(second, fixture.branch());
        subscribe(active, fixture.branch());
        waitForSubscribers(fixture.branch(), 3);
        UUID before = publish(fixture.branch());
        first.event(before);
        second.event(before);
        active.event(before);

        userService.deactivate(fixture.admin(), fixture.business(), fixture.cashier());
        waitUntil(() -> !first.session.isConnected() && !second.session.isConnected());
        UUID after = publish(fixture.branch());
        active.event(after);
        assertThat(first.received).isEmpty();
        assertThat(second.received).isEmpty();
        assertThat(active.session.isConnected()).isTrue();

        userService.activate(fixture.admin(), fixture.business(), fixture.cashier());
        Client fresh = connect(fixture.cashierName(), fixture.terminal());
        subscribe(fresh, fixture.branch());
        waitForSubscribers(fixture.branch(), 2);
        UUID reactivated = publish(fixture.branch());
        fresh.event(reactivated);
        active.event(reactivated);
        assertThat(first.session.isConnected()).isFalse();
        assertThat(second.session.isConnected()).isFalse();
    }

    @Test
    void inactiveUserCannotConnectWithPreviouslyIssuedJwt() throws Exception {
        String oldToken = token(fixture.cashierName());
        userService.deactivate(fixture.admin(), fixture.business(), fixture.cashier());
        assertThatThrownBy(() -> connectToken(oldToken, fixture.terminal()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void inactiveUserCannotAddSubscriptionToExistingConnection() throws Exception {
        Client client = connect(fixture.cashierName(), fixture.terminal());
        // Shared-database change without a local event, as when another backend commits it.
        jdbc.update("update app_user set active = false where id = ?", fixture.cashier());
        subscribe(client, fixture.branch());
        waitUntil(() -> !client.session.isConnected());
        assertThat(client.received).isEmpty();
    }

    @Test
    void broadcastSeesRemoteDeactivationBeforeDeliveringWithOneBatchQuery() throws Exception {
        Client first = connect(fixture.cashierName(), fixture.terminal());
        Client second = connect(fixture.cashierName(), fixture.terminal());
        Client active = connect(fixture.activeName(), fixture.terminal());
        Client foreign = connect(fixture.otherName(), fixture.otherTerminal());
        subscribe(first, fixture.branch());
        subscribe(second, fixture.branch());
        subscribe(active, fixture.branch());
        subscribe(foreign, fixture.otherBranch());
        waitForSubscribers(fixture.branch(), 3);
        waitForSubscribers(fixture.otherBranch(), 1);
        jdbc.update("update app_user set active = false where id = ?", fixture.cashier());
        assertThat(first.session.isConnected()).isTrue();

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        UUID event;
        try {
            event = publish(fixture.branch());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
        active.event(event);
        waitUntil(() -> !first.session.isConnected() && !second.session.isConnected());
        assertThat(first.received).isEmpty();
        assertThat(second.received).isEmpty();
        assertThat(foreign.received).isEmpty();
        foreign.event(publish(fixture.otherBranch()));
        assertThat(foreign.session.isConnected()).isTrue();
    }

    @Test
    void groupedPeriodicCheckClosesRemotelyDeactivatedUserWithoutWaitingForEvents() throws Exception {
        Client revoked = connect(fixture.cashierName(), fixture.terminal());
        Client active = connect(fixture.activeName(), fixture.terminal());
        subscribe(active, fixture.branch());
        waitForSubscribers(fixture.branch(), 1);
        jdbc.update("update app_user set active = false where id = ?", fixture.cashier());
        // Invoke the scheduled operation explicitly: no sleep or clock-dependent polling in tests.
        sessions.recheckConnectedUsers();
        waitUntil(() -> !revoked.session.isConnected());
        assertThat(active.session.isConnected()).isTrue();
        active.event(publish(fixture.branch()));
    }

    @Test
    void rolledBackDeactivationDoesNotCloseSessionsOrStopDelivery() throws Exception {
        Client cashier = connect(fixture.cashierName(), fixture.terminal());
        subscribe(cashier, fixture.branch());
        waitForSubscribers(fixture.branch(), 1);
        transactions.executeWithoutResult(status -> {
            userService.deactivate(fixture.admin(), fixture.business(), fixture.cashier());
            status.setRollbackOnly();
        });
        assertThat(cashier.session.isConnected()).isTrue();
        cashier.event(publish(fixture.branch()));
        assertThat(jdbc.queryForObject("select active from app_user where id = ?", Boolean.class,
                fixture.cashier())).isTrue();
    }

    @Test
    void crossBusinessSubscriptionIsRejected() throws Exception {
        Client client = connect(fixture.cashierName(), fixture.terminal());
        subscribe(client, fixture.otherBranch());
        waitUntil(() -> !client.session.isConnected());
        assertThat(client.received).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passwordChangeOrResetClosesOldSocketsAndRejectsOldJwt(boolean reset) throws Exception {
        String oldToken = token(fixture.cashierName());
        Client old = connectToken(oldToken, fixture.terminal());
        Client active = connect(fixture.activeName(), fixture.terminal());
        subscribe(old, fixture.branch());
        subscribe(active, fixture.branch());
        waitForSubscribers(fixture.branch(), 2);
        String password = "NuevaClave123!";
        if (reset) userService.resetPassword(fixture.admin(), fixture.business(), 0, fixture.cashier(),
                new ResetPasswordRequest(password));
        else userService.changePassword(fixture.cashier(), fixture.business(), 0,
                new ChangePasswordRequest(PASSWORD, password));
        waitUntil(() -> !old.session.isConnected());
        active.event(publish(fixture.branch()));
        assertThat(old.received).isEmpty();
        assertThatThrownBy(() -> connectToken(oldToken, fixture.terminal())).isInstanceOf(ExecutionException.class);
        String freshToken = auth.login(new LoginRequest(fixture.cashierName(), password)).accessToken();
        Client fresh = connectToken(freshToken, fixture.terminal());
        subscribe(fresh, fixture.branch());
        waitForSubscribers(fixture.branch(), 2);
        UUID next = publish(fixture.branch());
        active.event(next);
        fresh.event(next);
    }

    @Test
    void remoteCredentialChangeIsDetectedBeforeBroadcastAndOnNewSubscription() throws Exception {
        String token = token(fixture.cashierName());
        Client old = connectToken(token, fixture.terminal());
        Client unsubscribed = connectToken(token, fixture.terminal());
        Client active = connect(fixture.activeName(), fixture.terminal());
        subscribe(old, fixture.branch());
        subscribe(active, fixture.branch());
        waitForSubscribers(fixture.branch(), 2);
        jdbc.update("update app_user set password_hash = ?, credential_version = credential_version + 1 where id = ?",
                encoder.encode("Remota123!"), fixture.cashier());
        subscribe(unsubscribed, fixture.branch());
        waitUntil(() -> !unsubscribed.session.isConnected());
        active.event(publish(fixture.branch()));
        waitUntil(() -> !old.session.isConnected());
        assertThat(old.received).isEmpty();
        assertThatThrownBy(() -> connectToken(token, fixture.terminal())).isInstanceOf(ExecutionException.class);
    }

    private Client connect(String username, UUID terminal) throws Exception {
        return connectToken(token(username), terminal);
    }

    private Client connectToken(String token, UUID terminal) throws Exception {
        Client client = new Client();
        StompHeaders headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("X-Terminal-Id", terminal.toString());
        WebSocketHttpHeaders webHeaders = new WebSocketHttpHeaders();
        webHeaders.setOrigin("http://localhost:4200");
        client.session = stomp.connectAsync("ws://localhost:" + port + "/ws", webHeaders, headers,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        connected.add(client);
        return client;
    }

    private String token(String username) {
        return auth.login(new LoginRequest(username, PASSWORD)).accessToken();
    }

    private void subscribe(Client client, UUID branch) {
        client.session.subscribe(destination(branch), new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { client.received.add((String) payload); }
        });
    }

    private void waitForSubscribers(UUID branch, int count) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination(branch));
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        waitUntil(() -> broker.getSubscriptionRegistry().findSubscriptions(message).size() == count);
    }

    private UUID publish(UUID branch) {
        UUID sale = UUID.randomUUID();
        transactions.executeWithoutResult(status -> publisher.saleChanged(sale, branch));
        return sale;
    }

    private String destination(UUID branch) { return "/topic/branches/" + branch + "/sales"; }

    private void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertThat(condition.getAsBoolean()).as("WebSocket state reached before timeout").isTrue();
    }
}
