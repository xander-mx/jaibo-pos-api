package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.sale.api.request.CloseCreditSaleRequest;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.response.SaleResponse;
import com.eljaibo.pos.sale.application.SaleCreditService;
import com.eljaibo.pos.sale.application.SaleService;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.persistence.SaleRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
class CreditPaymentControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired CustomerRepository customers;
    @Autowired SaleRepository sales;
    @Autowired SaleService saleService;
    @Autowired SaleCreditService saleCreditService;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private Fixture fixture;

    private record Fixture(
            UUID business,
            UUID user,
            UUID terminal,
            UUID session,
            UUID customer
    ) {
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    /*
     * 1. ABONO PARCIAL
     */
    @Test
    void appliesPartialPaymentToReceivable()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("1000.00")
                );

        mvc.perform(
                        creditPayment(
                                fixture.customer(),
                                creditSale.receivableId(),
                                "CASH",
                                new BigDecimal("300.00")
                        )
                )
                .andExpect(
                        status().isNoContent()
                );

        BigDecimal balance =
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        creditSale.receivableId()
                );

        assertThat(balance)
                .isEqualByComparingTo("700.00");

        String receivableStatus =
                jdbc.queryForObject(
                        """
                        select status
                        from receivable
                        where id = ?
                        """,
                        String.class,
                        creditSale.receivableId()
                );

        assertThat(receivableStatus)
                .isEqualTo("OPEN");

        var payment =
                jdbc.queryForMap(
                        """
                        select customer_id,
                               payment_method,
                               amount,
                               cash_session_id
                        from credit_payment
                        where customer_id = ?
                        """,
                        fixture.customer()
                );

        assertThat(
                payment.get("payment_method")
        ).isEqualTo("CASH");

        assertThat(
                (BigDecimal) payment.get("amount")
        ).isEqualByComparingTo("300.00");

        assertThat(
                payment.get("cash_session_id")
        ).isEqualTo(fixture.session());

        var application =
                jdbc.queryForMap(
                        """
                        select cpa.amount,
                               cpa.receivable_id
                        from credit_payment_application cpa
                        join credit_payment cp
                          on cp.id = cpa.credit_payment_id
                        where cp.customer_id = ?
                        """,
                        fixture.customer()
                );

        assertThat(
                application.get("receivable_id")
        ).isEqualTo(
                creditSale.receivableId()
        );

        assertThat(
                (BigDecimal) application.get("amount")
        ).isEqualByComparingTo("300.00");
    }

    /*
     * 2. ABONO QUE LIQUIDA LA CUENTA
     */
    @Test
    void paysReceivableCompletely()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        creditPayment(
                                fixture.customer(),
                                creditSale.receivableId(),
                                "CASH",
                                new BigDecimal("500.00")
                        )
                )
                .andExpect(
                        status().isNoContent()
                );

        var receivable =
                jdbc.queryForMap(
                        """
                        select balance,
                               status,
                               paid_at
                        from receivable
                        where id = ?
                        """,
                        creditSale.receivableId()
                );

        assertThat(
                (BigDecimal) receivable.get("balance")
        ).isEqualByComparingTo("0.00");

        assertThat(
                receivable.get("status")
        ).isEqualTo("PAID");

        assertThat(
                receivable.get("paid_at")
        ).isNotNull();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id = ?
                        """,
                        Integer.class,
                        creditSale.receivableId()
                )
        ).isEqualTo(1);
    }

    /*
     * 3. NO PERMITIR ABONO MAYOR AL SALDO
     */
    @Test
    void rejectsPaymentGreaterThanReceivableBalance()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        creditPayment(
                                fixture.customer(),
                                creditSale.receivableId(),
                                "CASH",
                                new BigDecimal("600.00")
                        )
                )
                .andExpect(
                        status().isConflict()
                );

        BigDecimal balance =
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        creditSale.receivableId()
                );

        assertThat(balance)
                .isEqualByComparingTo("500.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isZero();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id = ?
                        """,
                        Integer.class,
                        creditSale.receivableId()
                )
        ).isZero();
    }

    /*
     * 4. NO PERMITIR APLICAR DEUDA DE OTRO CLIENTE
     */
    @Test
    void rejectsReceivableFromAnotherCustomer()
            throws Exception {

        UUID otherCustomer =
                transactions.execute(status ->
                        customers.save(
                                new Customer(
                                        businesses
                                                .findById(
                                                        fixture.business()
                                                )
                                                .orElseThrow(),
                                        "Otro cliente",
                                        null,
                                        null,
                                        true,
                                        false,
                                        false
                                )
                        ).getId()
                );

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        creditPayment(
                                otherCustomer,
                                creditSale.receivableId(),
                                "CARD",
                                new BigDecimal("100.00")
                        )
                )
                .andExpect(
                        status().isConflict()
                );

        BigDecimal balance =
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        creditSale.receivableId()
                );

        assertThat(balance)
                .isEqualByComparingTo("500.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        otherCustomer
                )
        ).isZero();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id = ?
                        """,
                        Integer.class,
                        creditSale.receivableId()
                )
        ).isZero();
    }

    /*
     * 5. CAJA CERRADA
     */
    @Test
    void rejectsPaymentWhenCashSessionIsClosed()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        jdbc.update(
                """
                update cash_session
                set status = 'CLOSED'
                where id = ?
                """,
                fixture.session()
        );

        mvc.perform(
                        creditPayment(
                                fixture.customer(),
                                creditSale.receivableId(),
                                "CASH",
                                new BigDecimal("100.00")
                        )
                )
                .andExpect(
                        status().isConflict()
                );

        BigDecimal balance =
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        creditSale.receivableId()
                );

        assertThat(balance)
                .isEqualByComparingTo("500.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isZero();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id = ?
                        """,
                        Integer.class,
                        creditSale.receivableId()
                )
        ).isZero();
    }

    @Test
    void appliesAutomaticPaymentToSingleReceivable()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CASH",
                                new BigDecimal("200.00")
                        )
                )
                .andExpect(status().isNoContent());

        BigDecimal balance =
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        creditSale.receivableId()
                );

        assertThat(balance)
                .isEqualByComparingTo("300.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id = ?
                        """,
                        Integer.class,
                        creditSale.receivableId()
                )
        ).isEqualTo(1);
    }

    @Test
    void appliesAutomaticPaymentUsingOldestReceivablesFirst()
            throws Exception {

        CreditSale first =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("200.00")
                );

        CreditSale second =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("350.00")
                );

        CreditSale third =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-01 10:00:00"),
                first.receivableId()
        );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-02 10:00:00"),
                second.receivableId()
        );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-03 10:00:00"),
                third.receivableId()
        );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CASH",
                                new BigDecimal("400.00")
                        )
                )
                .andExpect(status().isNoContent());

        BigDecimal firstBalance =
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        first.receivableId()
                );

        BigDecimal secondBalance =
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        second.receivableId()
                );

        BigDecimal thirdBalance =
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        third.receivableId()
                );

        assertThat(firstBalance)
                .isEqualByComparingTo("0.00");

        assertThat(secondBalance)
                .isEqualByComparingTo("150.00");

        assertThat(thirdBalance)
                .isEqualByComparingTo("500.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id in (?, ?, ?)
                        """,
                        Integer.class,
                        first.receivableId(),
                        second.receivableId(),
                        third.receivableId()
                )
        ).isEqualTo(2);
    }

    @Test
    void paysAllOpenReceivablesExactly()
            throws Exception {

        CreditSale first =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("200.00")
                );

        CreditSale second =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("300.00")
                );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-01 10:00:00"),
                first.receivableId()
        );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-02 10:00:00"),
                second.receivableId()
        );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "TRANSFER",
                                new BigDecimal("500.00")
                        )
                )
                .andExpect(status().isNoContent());

        var firstReceivable =
                jdbc.queryForMap(
                        """
                        select balance, status, paid_at
                        from receivable
                        where id = ?
                        """,
                        first.receivableId()
                );

        var secondReceivable =
                jdbc.queryForMap(
                        """
                        select balance, status, paid_at
                        from receivable
                        where id = ?
                        """,
                        second.receivableId()
                );

        assertThat(
                (BigDecimal) firstReceivable.get("balance")
        ).isEqualByComparingTo("0.00");

        assertThat(firstReceivable.get("status"))
                .isEqualTo("PAID");

        assertThat(firstReceivable.get("paid_at"))
                .isNotNull();

        assertThat(
                (BigDecimal) secondReceivable.get("balance")
        ).isEqualByComparingTo("0.00");

        assertThat(secondReceivable.get("status"))
                .isEqualTo("PAID");

        assertThat(secondReceivable.get("paid_at"))
                .isNotNull();

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id in (?, ?)
                        """,
                        Integer.class,
                        first.receivableId(),
                        second.receivableId()
                )
        ).isEqualTo(2);
    }

    @Test
    void rejectsAutomaticPaymentGreaterThanCustomerDebt()
            throws Exception {

        CreditSale first =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("200.00")
                );

        CreditSale second =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("300.00")
                );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CASH",
                                new BigDecimal("600.00")
                        )
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        first.receivableId()
                )
        ).isEqualByComparingTo("200.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select balance
                        from receivable
                        where id = ?
                        """,
                        BigDecimal.class,
                        second.receivableId()
                )
        ).isEqualByComparingTo("300.00");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id in (?, ?)
                        """,
                        Integer.class,
                        first.receivableId(),
                        second.receivableId()
                )
        ).isZero();
    }

    @Test
    void rejectsAutomaticPaymentWhenCustomerHasNoOpenReceivables()
            throws Exception {

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CARD",
                                new BigDecimal("100.00")
                        )
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                )
        ).isZero();
    }

    @Test
    void createsSingleCreditPaymentWhenDistributedAcrossMultipleReceivables()
            throws Exception {

        CreditSale first =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("150.00")
                );

        CreditSale second =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("250.00")
                );

        CreditSale third =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("400.00")
                );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-01 10:00:00"),
                first.receivableId()
        );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-02 10:00:00"),
                second.receivableId()
        );

        jdbc.update(
                "update receivable set opened_at = ? where id = ?",
                Timestamp.valueOf("2026-09-03 10:00:00"),
                third.receivableId()
        );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CARD",
                                new BigDecimal("500.00")
                        )
                )
                .andExpect(status().isNoContent());

        Integer paymentCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment
                        where customer_id = ?
                        """,
                        Integer.class,
                        fixture.customer()
                );

        assertThat(paymentCount)
                .isEqualTo(1);

        Integer applicationCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from credit_payment_application
                        where receivable_id in (?, ?, ?)
                        """,
                        Integer.class,
                        first.receivableId(),
                        second.receivableId(),
                        third.receivableId()
                );

        assertThat(applicationCount)
                .isEqualTo(3);

        assertThat(
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        first.receivableId()
                )
        ).isEqualByComparingTo("0.00");

        assertThat(
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        second.receivableId()
                )
        ).isEqualByComparingTo("0.00");

        assertThat(
                jdbc.queryForObject(
                        "select balance from receivable where id = ?",
                        BigDecimal.class,
                        third.receivableId()
                )
        ).isEqualByComparingTo("300.00");
    }

    @Test
    void listsOpenReceivablesForCustomer()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables",
                                        fixture.customer()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        creditSale
                                                .receivableId()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].saleId")
                                .value(
                                        creditSale
                                                .saleId()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].originalAmount")
                                .value(500.00)
                )
                .andExpect(
                        jsonPath("$[0].balance")
                                .value(500.00)
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("OPEN")
                );
    }

    @Test
    void doesNotListPaidReceivables()
            throws Exception {

        CreditSale creditSale =
                createCreditSale(
                        fixture.customer(),
                        new BigDecimal("500.00")
                );

        mvc.perform(
                        creditPayment(
                                fixture.customer(),
                                creditSale.receivableId(),
                                "CASH",
                                new BigDecimal("500.00")
                        )
                )
                .andExpect(status().isNoContent());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables",
                                        fixture.customer()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(0)
                );
    }

    @Test
    void returnsCustomerCreditPaymentHistory()
            throws Exception {

        createCreditSale(
                fixture.customer(),
                new BigDecimal("500.00")
        );

        mvc.perform(
                        automaticCreditPayment(
                                fixture.customer(),
                                "CASH",
                                new BigDecimal("100.00")
                        )
                )
                .andExpect(status().isNoContent());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/"
                                                + fixture.customer()
                                                + "/credit-payments"
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].receivedAt").exists());
    }

    @Test
    void doesNotReturnCreditPaymentsFromAnotherBusiness()
            throws Exception {

        Fixture other = createFixture();

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/"
                                                + other.customer()
                                                + "/credit-payments"
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isNotFound());
    }

    /*
     * HELPERS
     */

    private CreditSale createCreditSale(
            UUID customerId,
            BigDecimal total
    ) {

        SaleResponse opened =
                saleService.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Crédito prueba",
                                customerId
                        )
                );

        transactions.executeWithoutResult(status -> {

            Sale sale =
                    sales.findById(
                            opened.id()
                    ).orElseThrow();

            sale.refreshTotals(
                    total,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        });

        saleCreditService.closeAsCredit(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                opened.id(),
                new CloseCreditSaleRequest(
                        List.of()
                )
        );

        UUID receivableId =
                jdbc.queryForObject(
                        """
                        select id
                        from receivable
                        where sale_id = ?
                        """,
                        UUID.class,
                        opened.id()
                );

        return new CreditSale(
                opened.id(),
                receivableId
        );
    }

    private MockHttpServletRequestBuilder creditPayment(
            UUID customerId,
            UUID receivableId,
            String paymentMethod,
            BigDecimal amount
    ) {
        String body = """
            {
              "receivableId": "%s",
              "paymentMethod": "%s",
              "amount": %s
            }
            """.formatted(
                receivableId,
                paymentMethod,
                amount.toPlainString()
        );

        return authenticated(
                post(
                        "/api/v1/customers/{customerId}/credit-payments",
                        customerId
                ),
                fixture.terminal()
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder
    authenticated(
            MockHttpServletRequestBuilder request,
            UUID terminal
    ) {
        return request
                .with(
                        jwt().jwt(token ->
                                token
                                        .subject("cashier")
                                        .claim(
                                                "user_id",
                                                fixture.user().toString()
                                        )
                                        .claim(
                                                "business_id",
                                                fixture.business().toString()
                                        )
                                        .claim(
                                                "roles",
                                                List.of("CASHIER")
                                        )
                        )
                )
                .header(
                        "X-Terminal-Id",
                        terminal
                );
    }

    private Fixture createFixture() {

        return transactions.execute(status -> {

            Business business =
                    businesses.save(
                            new Business(
                                    "Jaibo",
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
                                    "cashier-"
                                            + UUID.randomUUID(),
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

            OperatingCycle cycle =
                    cycles.save(
                            OperatingCycle.open(
                                    branch,
                                    LocalDate.of(
                                            2026,
                                            9,
                                            4
                                    )
                            )
                    );

            CashSession session =
                    sessions.save(
                            new CashSession(
                                    cycle,
                                    LocalDate.of(
                                            2026,
                                            9,
                                            6
                                    ),
                                    new BigDecimal(
                                            "500.00"
                                    ),
                                    user,
                                    terminal
                            )
                    );

            Customer customer =
                    customers.save(
                            new Customer(
                                    business,
                                    "Cliente",
                                    null,
                                    null,
                                    true,
                                    false,
                                    false
                            )
                    );

            return new Fixture(
                    business.getId(),
                    user.getId(),
                    terminal.getId(),
                    session.getId(),
                    customer.getId()
            );
        });
    }

    private record CreditSale(
            UUID saleId,
            UUID receivableId
    ) {
    }

    private MockHttpServletRequestBuilder automaticCreditPayment(
            UUID customerId,
            String paymentMethod,
            BigDecimal amount
    ) {
        String body = """
            {
              "paymentMethod": "%s",
              "amount": %s
            }
            """.formatted(
                paymentMethod,
                amount.toPlainString()
        );

        return authenticated(
                post(
                        "/api/v1/customers/{customerId}/credit-payments/automatic",
                        customerId
                ),
                fixture.terminal()
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}