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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class ReceivableControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired SaleService saleService;
    @Autowired SaleRepository sales;
    @Autowired SaleCreditService saleCreditService;
    @Autowired CustomerRepository customers;
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

    private record CreditSale(
            UUID saleId,
            UUID receivableId
    ) {
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void returnsOpenReceivables()
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
    void returnsPaidReceivables()
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
                                new BigDecimal("500.00")
                        )
                )
                .andExpect(status().isNoContent());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables/paid",
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
                                .value(0.00)
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("PAID")
                )
                .andExpect(
                        jsonPath("$[0].paidAt")
                                .exists()
                );
    }

    @Test
    void doesNotReturnOpenReceivableAsPaid()
            throws Exception {

        createCreditSale(
                fixture.customer(),
                new BigDecimal("500.00")
        );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables/paid",
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
    void rejectsReceivablesFromAnotherBusiness()
            throws Exception {

        Fixture other =
                createFixture();

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables",
                                        other.customer()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(
                        status().isNotFound()
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/customers/{customerId}/receivables/paid",
                                        other.customer()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    /*
     * HELPERS
     */

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

    private MockHttpServletRequestBuilder
    automaticCreditPayment(
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
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .content(body);
    }
}
