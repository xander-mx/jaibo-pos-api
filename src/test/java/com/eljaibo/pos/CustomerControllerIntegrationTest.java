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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class CustomerControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired AppUserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired OperatingCycleRepository cycles;
    @Autowired CashSessionRepository sessions;
    @Autowired CustomerRepository customers;
    @Autowired TransactionTemplate transactions;

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

    @Test
    void updatesCustomer() throws Exception {
        mvc.perform(
                        authenticated(
                                patch(
                                        "/api/v1/customers/{customerId}",
                                        fixture.customer()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "name": "Cliente actualizado",
                              "phone": "3312345678",
                              "email": "cliente@email.com",
                              "frequent": true,
                              "wholesale": true,
                              "creditEnabled": true
                            }
                            """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente actualizado"))
                .andExpect(jsonPath("$.phone").value("3312345678"))
                .andExpect(jsonPath("$.email").value("cliente@email.com"))
                .andExpect(jsonPath("$.frequent").value(true))
                .andExpect(jsonPath("$.wholesale").value(true))
                .andExpect(jsonPath("$.creditEnabled").value(true));
    }

    @Test
    void rejectsUpdatingCustomerFromAnotherBusiness()
            throws Exception {

        Fixture other = createFixture();

        mvc.perform(
                        authenticated(
                                patch(
                                        "/api/v1/customers/{customerId}",
                                        other.customer()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "name": "No permitido",
                              "phone": null,
                              "email": null,
                              "frequent": false,
                              "wholesale": false,
                              "creditEnabled": false
                            }
                            """)
                )
                .andExpect(status().isNotFound());
    }
}
