package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.cash.api.CloseCashSessionRequest;
import com.eljaibo.pos.cash.api.CashSessionResponse;
import com.eljaibo.pos.cash.application.CashSessionService;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.purchase.api.request.RegisterPurchasePaymentRequest;
import com.eljaibo.pos.purchase.api.response.PurchasePaymentResponse;
import com.eljaibo.pos.purchase.application.PurchasePaymentService;
import com.eljaibo.pos.purchase.domain.Purchase;
import com.eljaibo.pos.purchase.domain.PurchasePaymentSource;
import com.eljaibo.pos.purchase.persistence.PurchaseRepository;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PurchasePaymentServiceIntegrationTest {

    private static final int TIMEOUT_SECONDS = 15;
    private static final BigDecimal PURCHASE_TOTAL = new BigDecimal("100.00");
    private static final BigDecimal OPENING_AMOUNT = new BigDecimal("500.00");

    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired PurchaseRepository purchases;
    @Autowired PurchasePaymentService payments;
    @Autowired CashSessionService cashSessions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Fixture fixture;

    private record Fixture(UUID business, UUID user, UUID tablet, UUID phone,
                           UUID session, UUID purchase) { }

    @BeforeEach
    void setUp() {
        // No test-wide transaction: fixtures must be committed and visible to both workers.
        fixture = inNewTransaction(() -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Branch branch = branches.save(new Branch(business, "MAIN", "Principal"));
            AppUser admin = users.save(new AppUser(business, "admin-" + UUID.randomUUID(),
                    "test-hash", "Administrador", UserRole.ADMIN));
            Terminal tablet = terminals.save(new Terminal(branch, admin, "Tablet"));
            Terminal phone = terminals.save(new Terminal(branch, admin, "Celular"));
            OperatingCycle cycle = cycles.save(OperatingCycle.open(branch, LocalDate.of(2026, 10, 2)));
            CashSession session = sessions.save(new CashSession(cycle, LocalDate.of(2026, 10, 4),
                    OPENING_AMOUNT, admin, tablet));
            Purchase purchase = new Purchase(branch, admin, null, "Compra de prueba");
            purchase.updateTotal(PURCHASE_TOTAL);
            purchase.markReceived(Instant.parse("2026-10-04T18:00:00Z"));
            purchases.save(purchase);
            return new Fixture(business.getId(), admin.getId(), tablet.getId(), phone.getId(),
                    session.getId(), purchase.getId());
        });
    }

    @Test
    void concurrentPaymentsCannotExceedPurchaseBalance() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch firstPaymentRegistered = new CountDownLatch(1);
        CountDownLatch releaseFirstPayment = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PurchasePaymentResponse> first = executor.submit(() -> inNewTransaction(() -> {
                firstPid.set(backendPid());
                ready.countDown();
                await(start);
                // External payments ensure that only the purchase lock protects the balance.
                PurchasePaymentResponse payment = register("80.00", PurchasePaymentSource.EXTERNAL, fixture.tablet());
                entityManager.flush();
                firstPaymentRegistered.countDown();
                await(releaseFirstPayment);
                return payment;
            }));
            Future<ResponseStatusException> second = executor.submit(() -> {
                try {
                    inNewTransaction(() -> {
                        secondPid.set(backendPid());
                        ready.countDown();
                        await(start);
                        await(firstPaymentRegistered);
                        return register("80.00", PurchasePaymentSource.EXTERNAL, fixture.phone());
                    });
                    throw new AssertionError("Second payment should exceed the remaining balance");
                } catch (ResponseStatusException exception) {
                    return exception;
                }
            });

            await(ready);
            start.countDown();
            await(firstPaymentRegistered);
            assertBlockedBy(secondPid.get(), firstPid.get(), second);
            releaseFirstPayment.countDown();

            PurchasePaymentResponse accepted = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ResponseStatusException rejected = second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(rejected.getReason()).isEqualTo("Payment exceeds purchase balance");
            assertThat(paymentIds()).containsExactly(accepted.id());
            assertThat(paidTotal()).isEqualByComparingTo("80.00").isLessThanOrEqualTo(PURCHASE_TOTAL);
            assertThat(cashMovementCount()).isZero();
        } finally {
            start.countDown();
            releaseFirstPayment.countDown();
            shutdown(executor);
        }
    }

    @ParameterizedTest(name = "payment and closing coordinate, paymentFirst={0}")
    @ValueSource(booleans = {true, false})
    void cashPaymentAndClosingShareTheCashSessionLock(boolean paymentFirst) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch firstOperationCompleted = new CountDownLatch(1);
        CountDownLatch releaseFirstOperation = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        BigDecimal expected = paymentFirst ? new BigDecimal("420.00") : OPENING_AMOUNT;
        try {
            Future<Object> first = executor.submit(() -> inNewTransaction(() -> {
                firstPid.set(backendPid());
                ready.countDown();
                await(start);
                Object result = paymentFirst
                        ? register("80.00", PurchasePaymentSource.CASH_REGISTER, fixture.tablet())
                        : close(expected);
                entityManager.flush();
                firstOperationCompleted.countDown();
                // Keep the actual service locks until the observer confirms PostgreSQL is waiting.
                await(releaseFirstOperation);
                return result;
            }));
            Future<Object> second = executor.submit(() -> {
                try {
                    return inNewTransaction(() -> {
                        secondPid.set(backendPid());
                        ready.countDown();
                        await(start);
                        await(firstOperationCompleted);
                        return paymentFirst
                                ? close(expected)
                                : register("80.00", PurchasePaymentSource.CASH_REGISTER, fixture.phone());
                    });
                } catch (ResponseStatusException exception) {
                    return exception;
                }
            });

            await(ready);
            start.countDown();
            await(firstOperationCompleted);
            assertBlockedBy(secondPid.get(), firstPid.get(), second);
            releaseFirstOperation.countDown();
            Object firstResult = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Object secondResult = second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            CashSessionResponse closed;
            if (paymentFirst) {
                assertThat(secondResult).isInstanceOf(CashSessionResponse.class);
                closed = (CashSessionResponse) secondResult;
                PurchasePaymentResponse payment = (PurchasePaymentResponse) firstResult;
                assertThat(paymentIds()).containsExactly(payment.id());
                assertThat(paidTotal()).isEqualByComparingTo("80.00");
                assertCashPaymentMovement(payment);
            } else {
                assertThat(secondResult).isInstanceOf(ResponseStatusException.class);
                ResponseStatusException rejected = (ResponseStatusException) secondResult;
                assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rejected.getReason()).isEqualTo("An open cash session is required");
                closed = (CashSessionResponse) firstResult;
                assertThat(paymentIds()).isEmpty();
                assertThat(paidTotal()).isEqualByComparingTo("0.00");
                assertThat(cashMovementCount()).isZero();
            }
            assertThat(closed.closingExpectedAmount()).isEqualByComparingTo(expected);
            assertThat(closed.closingDifference()).isEqualByComparingTo("0.00");
            assertClosedCashIsConsistent(expected);
        } finally {
            start.countDown();
            releaseFirstOperation.countDown();
            shutdown(executor);
        }
    }

    @Test
    void fullyPaidPurchaseRejectsAdditionalPaymentWithConflict() {
        PurchasePaymentResponse first = inNewTransaction(() ->
                register("40.00", PurchasePaymentSource.CASH_REGISTER, fixture.tablet()));
        PurchasePaymentResponse second = inNewTransaction(() ->
                register("60.00", PurchasePaymentSource.CASH_REGISTER, fixture.phone()));

        assertThatThrownBy(() -> inNewTransaction(() ->
                register("0.01", PurchasePaymentSource.CASH_REGISTER, fixture.tablet())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("Payment exceeds purchase balance");
                });

        assertThat(paymentIds()).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(paidTotal()).isEqualByComparingTo(PURCHASE_TOTAL);
        assertCashPaymentMovement(first);
        assertCashPaymentMovement(second);
        assertThat(cashMovementCount()).isEqualTo(2);
        assertThat(cashOutTotal()).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("select status from cash_session where id = ?",
                String.class, fixture.session())).isEqualTo("OPEN");
    }

    private PurchasePaymentResponse register(String amount, PurchasePaymentSource source, UUID terminal) {
        return payments.register(fixture.user(), fixture.business(), terminal, fixture.purchase(),
                new RegisterPurchasePaymentRequest(PaymentMethod.CASH, source, new BigDecimal(amount)));
    }

    private CashSessionResponse close(BigDecimal countedAmount) {
        // Separate device from the first cash payment.
        return cashSessions.close(fixture.user(), fixture.business(), fixture.phone(), fixture.session(),
                new CloseCashSessionRequest(countedAmount));
    }

    private <T> T inNewTransaction(Callable<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(TIMEOUT_SECONDS * 3);
        return transaction.execute(status -> {
            // Bound blocking SQL as well as Java waits to keep failures from hanging the suite.
            jdbc.execute("set local lock_timeout = '20s'");
            try {
                return action.call();
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private int backendPid() {
        return Objects.requireNonNull(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
    }

    private void assertBlockedBy(int waitingPid, int holderPid, Future<?> waitingOperation) {
        assertThat(waitingPid).isNotEqualTo(holderPid);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",
                    Boolean.class, holderPid, waitingPid);
            if (Boolean.TRUE.equals(blocked)) {
                assertThat(waitingOperation).isNotDone();
                return;
            }
            if (waitingOperation.isDone()) break;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("Expected backend " + waitingPid + " to wait for transaction " + holderPid);
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("Concurrent operation reached synchronization point").isTrue();
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private List<UUID> paymentIds() {
        return jdbc.queryForList("select id from purchase_payment where purchase_id = ?",
                UUID.class, fixture.purchase());
    }

    private BigDecimal paidTotal() {
        return jdbc.queryForObject("select coalesce(sum(amount), 0) from purchase_payment where purchase_id = ?",
                BigDecimal.class, fixture.purchase());
    }

    private int cashMovementCount() {
        return Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from cash_movement where cash_session_id = ?",
                Integer.class, fixture.session()));
    }

    private BigDecimal cashOutTotal() {
        return jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from cash_movement
                where cash_session_id = ? and direction = 'OUT'
                """, BigDecimal.class, fixture.session());
    }

    private void assertCashPaymentMovement(PurchasePaymentResponse payment) {
        var persistedPayment = jdbc.queryForMap("select * from purchase_payment where id = ?", payment.id());
        assertThat(persistedPayment.get("purchase_id")).isEqualTo(fixture.purchase());
        assertThat(persistedPayment.get("cash_session_id")).isEqualTo(fixture.session());
        assertThat(persistedPayment.get("payment_source")).isEqualTo("CASH_REGISTER");
        assertThat(persistedPayment.get("payment_method")).isEqualTo("CASH");
        var movements = jdbc.queryForList("select * from cash_movement where reference_id = ?", payment.id());
        assertThat(movements).hasSize(1);
        var movement = movements.getFirst();
        assertThat(movement.get("cash_session_id")).isEqualTo(fixture.session());
        assertThat(movement.get("direction")).isEqualTo("OUT");
        assertThat(movement.get("movement_type")).isEqualTo("PURCHASE_PAYMENT");
        assertThat(movement.get("reference_type")).isEqualTo("PURCHASE_PAYMENT");
        assertThat((BigDecimal) movement.get("amount")).isEqualByComparingTo(payment.amount());
        assertThat(movement.get("performed_by_id")).isEqualTo(fixture.user());
        assertThat(movement.get("terminal_id")).isEqualTo(persistedPayment.get("terminal_id"));
    }

    private void assertClosedCashIsConsistent(BigDecimal expected) {
        var session = jdbc.queryForMap("select * from cash_session where id = ?", fixture.session());
        assertThat(session.get("status")).isEqualTo("CLOSED");
        assertThat((BigDecimal) session.get("closing_expected_amount")).isEqualByComparingTo(expected);
        assertThat((BigDecimal) session.get("closing_counted_amount")).isEqualByComparingTo(expected);
        assertThat((BigDecimal) session.get("closing_difference")).isEqualByComparingTo("0.00");
        assertThat(OPENING_AMOUNT.subtract(cashOutTotal())).isEqualByComparingTo(expected);
    }
}
