package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.response.SaleResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class SalePaymentControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired CustomerRepository customers;
    @Autowired TransactionTemplate transactions;
    @Autowired SaleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired SaleRepository sales;

    private Fixture fixture;

    private record Fixture(UUID business, UUID user, UUID terminal, UUID phone,
                           UUID session, UUID customer) { }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    private Fixture createFixture() {
        // Committed fixtures are visible to independent concurrent transactions.
        return transactions.execute(status -> {
            Business business = businesses.save(new Business("Jaibo", null, "MXN", "America/Mexico_City"));
            Branch branch = branches.save(new Branch(business, "MAIN", "Principal"));
            AppUser user = users.save(new AppUser(business, "cashier-" + UUID.randomUUID(),
                    "test-hash", "Cajero", UserRole.CASHIER));
            Terminal tablet = terminals.save(new Terminal(branch, user, "Tablet"));
            Terminal phone = terminals.save(new Terminal(branch, user, "Celular"));
            OperatingCycle cycle = cycles.save(OperatingCycle.open(branch, LocalDate.of(2026, 9, 4)));
            CashSession session = sessions.save(new CashSession(cycle, LocalDate.of(2026, 9, 6),
                    new BigDecimal("500.00"), user, tablet));
            Customer customer = customers.save(new Customer(business, "Cliente", null, null,
                    true, false, false));
            return new Fixture(business.getId(), user.getId(), tablet.getId(), phone.getId(),
                    session.getId(), customer.getId());
        });
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UUID terminal) {
        return request.with(jwt().jwt(token -> token.subject("cashier")
                        .claim("user_id", fixture.user().toString())
                        .claim("business_id", fixture.business().toString())
                        .claim("roles", List.of("CASHIER"))))
                .header("X-Terminal-Id", terminal);
    }

    private MockHttpServletRequestBuilder open(UUID session, UUID customer) {
        String body = "{\"cashSessionId\":\"" + session + "\",\"reference\":\" Mesa 1 \""
                + (customer == null ? "" : ",\"customerId\":\"" + customer + "\"") + "}";
        return authenticated(post("/api/v1/sales/open"), fixture.terminal())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void paysSaleWithCash() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": [
                                        {
                                          "paymentMethod": "CASH",
                                          "amount": 500
                                        }
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.total").value(500));

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_payment
                        where sale_id = ?
                        """,
                        Integer.class,
                        saleId
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select payment_method
                        from sale_payment
                        where sale_id = ?
                        """,
                        String.class,
                        saleId
                )
        ).isEqualTo("CASH");
    }

    @Test
    void paysSaleWithCombinedPayments() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": [
                                        {
                                          "paymentMethod": "CASH",
                                          "amount": 200
                                        },
                                        {
                                          "paymentMethod": "TRANSFER",
                                          "amount": 200
                                        },
                                        {
                                          "paymentMethod": "CARD",
                                          "amount": 100
                                        }
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.total").value(500));

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_payment
                        where sale_id = ?
                        """,
                        Integer.class,
                        saleId
                )
        ).isEqualTo(3);

        BigDecimal paidTotal = jdbc.queryForObject(
                """
                select sum(amount)
                from sale_payment
                where sale_id = ?
                """,
                BigDecimal.class,
                saleId
        );

        assertThat(paidTotal)
                .isEqualByComparingTo("500.00");
    }

    @Test
    void rejectsPaymentBelowSaleTotal() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": [
                                        {
                                          "paymentMethod": "CASH",
                                          "amount": 400
                                        }
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select status
                        from sale
                        where id = ?
                        """,
                        String.class,
                        saleId
                )
        ).isEqualTo("OPEN");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_payment
                        where sale_id = ?
                        """,
                        Integer.class,
                        saleId
                )
        ).isZero();
    }

    @Test
    void rejectsPaymentAboveSaleTotal() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": [
                                        {
                                          "paymentMethod": "CASH",
                                          "amount": 600
                                        }
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select status
                        from sale
                        where id = ?
                        """,
                        String.class,
                        saleId
                )
        ).isEqualTo("OPEN");
    }

    @Test
    void rejectsPayingAlreadyPaidSale() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        String body = """
            {
              "payments": [
                {
                  "paymentMethod": "CASH",
                  "amount": 500
                }
              ]
            }
            """;

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk());

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_payment
                        where sale_id = ?
                        """,
                        Integer.class,
                        saleId
                )
        ).isEqualTo(1);
    }

    @Test
    void rejectsPaymentWithoutPayments() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": []
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsPaidSaleAuditInformation() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                post("/api/v1/sales/{saleId}/pay", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "payments": [
                                        {
                                          "paymentMethod": "CARD",
                                          "amount": 500
                                        }
                                      ]
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        var sale = jdbc.queryForMap(
                """
                select status,
                       finalized_at,
                       finalized_by_user_id,
                       finalized_from_terminal_id
                from sale
                where id = ?
                """,
                saleId
        );

        assertThat(sale.get("status"))
                .isEqualTo("PAID");

        assertThat(sale.get("finalized_at"))
                .isNotNull();

        assertThat(sale.get("finalized_by_user_id"))
                .isEqualTo(fixture.user());

        assertThat(sale.get("finalized_from_terminal_id"))
                .isEqualTo(fixture.terminal());

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_event
                        where sale_id = ?
                          and event_type = 'SALE_PAID'
                        """,
                        Integer.class,
                        saleId
                )
        ).isEqualTo(1);
    }

    private UUID createOpenSaleWithTotals(
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            BigDecimal courtesyTotal
    ) {
        SaleResponse opened = service.open(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                new OpenSaleRequest(
                        fixture.session(),
                        "Prueba descuento",
                        null
                )
        );

        transactions.executeWithoutResult(status -> {
            Sale sale = sales.findById(opened.id())
                    .orElseThrow();

            sale.refreshTotals(
                    subtotal,
                    lineDiscountTotal,
                    courtesyTotal
            );
        });

        return opened.id();
    }
}
