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
import com.eljaibo.pos.sale.api.request.ApplySaleDiscountRequest;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.response.SaleDetailResponse;
import com.eljaibo.pos.sale.api.response.SaleResponse;
import com.eljaibo.pos.sale.application.SaleService;
import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleStatus;
import com.eljaibo.pos.sale.persistence.SaleRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SaleControllerIntegrationTest {
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
    void opensAndListsSharedAccountsWithAtomicAudit() throws Exception {
        String response = mvc.perform(open(fixture.session(), fixture.customer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.saleNumber").value(1))
                .andExpect(jsonPath("$.reference").value("Mesa 1"))
                .andExpect(jsonPath("$.customerId").value(fixture.customer().toString()))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.courtesyTotal").value(0))
                .andExpect(jsonPath("$.openedByUserId").value(fixture.user().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        mvc.perform(open(fixture.session(), null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleNumber").value(2));
        mvc.perform(authenticated(get("/api/v1/sales/open"), fixture.phone()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        var event = jdbc.queryForMap("select * from sale_event where sale_id = ?", id);
        assertThat(event.get("event_type")).isEqualTo("SALE_OPENED");
        assertThat(event.get("performed_by_id")).isEqualTo(fixture.user());
        assertThat(event.get("terminal_id")).isEqualTo(fixture.terminal());
        assertThat(event.get("occurred_at")).isNotNull();
    }

    @Test
    void rejectsClosedCashWithoutCreatingAccountOrEvent() throws Exception {
        jdbc.update("update cash_session set status = 'CLOSED' where id = ?", fixture.session());
        mvc.perform(open(fixture.session(), null)).andExpect(status().isConflict());
        assertThat(accountCount()).isZero();
    }

    @Test
    void isolatesBusinessesAndRejectsForeignCustomerTerminalAndSession() throws Exception {
        Fixture other = createFixture();
        service.open(other.user(), other.business(), other.terminal(),
                new OpenSaleRequest(other.session(), "Otra cuenta", null));
        mvc.perform(open(other.session(), null)).andExpect(status().isNotFound());
        mvc.perform(open(fixture.session(), other.customer())).andExpect(status().isNotFound());
        mvc.perform(authenticated(get("/api/v1/sales/open"), other.terminal()))
                .andExpect(status().isNotFound());
        mvc.perform(authenticated(get("/api/v1/sales/open"), fixture.terminal()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(accountCount()).isZero();
        mvc.perform(open(fixture.session(), null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleNumber").value(1));
    }

    @Test
    void isolatesBranchesWithinSameBusiness() throws Exception {
        UUID otherTerminal = transactions.execute(status -> {
            Branch branch = branches.save(new Branch(businesses.findById(fixture.business()).orElseThrow(),
                    "SECOND", "Otra sucursal"));
            return terminals.save(new Terminal(branch, users.findById(fixture.user()).orElseThrow(), "Otra"))
                    .getId();
        });
        mvc.perform(open(fixture.session(), null)).andExpect(status().isCreated());
        mvc.perform(authenticated(get("/api/v1/sales/open"), otherTerminal))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(authenticated(post("/api/v1/sales/open"), otherTerminal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashSessionId\":\"" + fixture.session() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidIdentityAndInactiveUser() throws Exception {
        Fixture other = createFixture();
        mvc.perform(get("/api/v1/sales/open").header("X-Terminal-Id", fixture.terminal())
                        .with(jwt().jwt(token -> token.claim("user_id", other.user().toString())
                                .claim("business_id", fixture.business().toString()))))
                .andExpect(status().isUnauthorized());
        jdbc.update("update app_user set active = false where id = ?", fixture.user());
        mvc.perform(open(fixture.session(), null)).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationAndValidRequest() throws Exception {
        mvc.perform(get("/api/v1/sales/open")).andExpect(status().isUnauthorized());
        mvc.perform(authenticated(post("/api/v1/sales/open"), fixture.terminal())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authenticated(post("/api/v1/sales/open"), fixture.terminal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashSessionId\":\"" + fixture.session()
                                + "\",\"reference\":\"" + "x".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatesUniqueConsecutiveNumbersAcrossConcurrentDevices() throws Exception {
        int count = 8;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<SaleResponse>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID terminal = i % 2 == 0 ? fixture.terminal() : fixture.phone();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return service.open(fixture.user(), fixture.business(), terminal,
                            new OpenSaleRequest(fixture.session(), null, null));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> numbers = new ArrayList<>();
            for (Future<SaleResponse> future : futures) {
                numbers.add(future.get(30, TimeUnit.SECONDS).saleNumber());
            }
            assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        }
        assertThat(accountCount()).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(*) from sale_event e join sale s on s.id = e.sale_id "
                        + "where s.cash_session_id = ? and e.event_type = 'SALE_OPENED'", Integer.class,
                fixture.session())).isEqualTo(count);
    }

    @Test
    void shouldApplyPercentageSaleDiscount() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        SaleDetailResponse response =
                service.applySaleDiscount(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        saleId,
                        new ApplySaleDiscountRequest(
                                DiscountType.PERCENT,
                                new BigDecimal("10")
                        )
                );

        assertThat(response.saleDiscountType())
                .isEqualTo(DiscountType.PERCENT);

        assertThat(response.saleDiscountValue())
                .isEqualByComparingTo("10.0000");

        assertThat(response.saleDiscountAmount())
                .isEqualByComparingTo("50.00");

        assertThat(response.total())
                .isEqualByComparingTo("450.00");
    }

    @Test
    void shouldReplaceExistingSaleDiscount() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        service.applySaleDiscount(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                saleId,
                new ApplySaleDiscountRequest(
                        DiscountType.PERCENT,
                        new BigDecimal("10")
                )
        );

        SaleDetailResponse response =
                service.applySaleDiscount(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        saleId,
                        new ApplySaleDiscountRequest(
                                DiscountType.AMOUNT,
                                new BigDecimal("80")
                        )
                );

        assertThat(response.saleDiscountType())
                .isEqualTo(DiscountType.AMOUNT);

        assertThat(response.saleDiscountValue())
                .isEqualByComparingTo("80.0000");

        assertThat(response.saleDiscountAmount())
                .isEqualByComparingTo("80.00");

        assertThat(response.total())
                .isEqualByComparingTo("420.00");
    }

    @Test
    void shouldRemoveSaleDiscount() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        service.applySaleDiscount(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                saleId,
                new ApplySaleDiscountRequest(
                        DiscountType.PERCENT,
                        new BigDecimal("10")
                )
        );

        SaleDetailResponse response =
                service.removeSaleDiscount(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        saleId
                );

        assertThat(response.saleDiscountType())
                .isEqualTo(DiscountType.NONE);

        assertThat(response.saleDiscountValue())
                .isEqualByComparingTo("0");

        assertThat(response.saleDiscountAmount())
                .isEqualByComparingTo("0");

        assertThat(response.total())
                .isEqualByComparingTo("500.00");
    }

    @Test
    void shouldCalculateSaleDiscountAfterLineDiscountsAndCourtesies() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                new BigDecimal("20.00"),
                new BigDecimal("50.00")
        );

        SaleDetailResponse response =
                service.applySaleDiscount(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        saleId,
                        new ApplySaleDiscountRequest(
                                DiscountType.PERCENT,
                                new BigDecimal("10")
                        )
                );

        assertThat(response.subtotal())
                .isEqualByComparingTo("500.00");

        assertThat(response.lineDiscountTotal())
                .isEqualByComparingTo("20.00");

        assertThat(response.courtesyTotal())
                .isEqualByComparingTo("50.00");

        assertThat(response.saleDiscountAmount())
                .isEqualByComparingTo("43.00");

        assertThat(response.total())
                .isEqualByComparingTo("387.00");
    }

    @Test
    void shouldCapFixedDiscountAtAvailableAmount() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        SaleDetailResponse response =
                service.applySaleDiscount(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        saleId,
                        new ApplySaleDiscountRequest(
                                DiscountType.AMOUNT,
                                new BigDecimal("500")
                        )
                );

        assertThat(response.saleDiscountAmount())
                .isEqualByComparingTo("100.00");

        assertThat(response.total())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectPercentageGreaterThanOneHundred() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.applySaleDiscount(
                                fixture.user(),
                                fixture.business(),
                                fixture.terminal(),
                                saleId,
                                new ApplySaleDiscountRequest(
                                        DiscountType.PERCENT,
                                        new BigDecimal("101")
                                )
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(exception.getReason())
                .isEqualTo("Percentage cannot exceed 100");
    }

    @Test
    void shouldAuditSaleDiscountChanges() {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        service.applySaleDiscount(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                saleId,
                new ApplySaleDiscountRequest(
                        DiscountType.PERCENT,
                        new BigDecimal("10")
                )
        );

        service.removeSaleDiscount(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                saleId
        );

        List<String> eventTypes = jdbc.queryForList(
                """
                select event_type
                from sale_event
                where sale_id = ?
                order by occurred_at, created_at
                """,
                String.class,
                saleId
        );

        assertThat(eventTypes).containsExactly(
                "SALE_OPENED",
                "SALE_DISCOUNT_APPLIED",
                "SALE_DISCOUNT_REMOVED"
        );
    }

    @Test
    void appliesSaleDiscountThroughHttp() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                patch("/api/v1/sales/{saleId}/discount", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "discountType": "PERCENT",
                                      "discountValue": 10
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleDiscountType").value("PERCENT"))
                .andExpect(jsonPath("$.saleDiscountValue").value(10))
                .andExpect(jsonPath("$.saleDiscountAmount").value(50))
                .andExpect(jsonPath("$.total").value(450));
    }

    @Test
    void removesSaleDiscountThroughHttp() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        service.applySaleDiscount(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                saleId,
                new ApplySaleDiscountRequest(
                        DiscountType.PERCENT,
                        new BigDecimal("10")
                )
        );

        mvc.perform(
                        authenticated(
                                delete("/api/v1/sales/{saleId}/discount", saleId),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleDiscountType").value("NONE"))
                .andExpect(jsonPath("$.saleDiscountValue").value(0))
                .andExpect(jsonPath("$.saleDiscountAmount").value(0))
                .andExpect(jsonPath("$.total").value(500));
    }

    @Test
    void rejectsInvalidSaleDiscountThroughHttp() throws Exception {
        UUID saleId = createOpenSaleWithTotals(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mvc.perform(
                        authenticated(
                                patch("/api/v1/sales/{saleId}/discount", saleId),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "discountType": "PERCENT",
                                      "discountValue": 101
                                    }
                                    """)
                )
                .andExpect(status().isConflict());
    }

    @Test
    void assignsCustomerToOpenSale()
            throws Exception {

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Cliente prueba",
                                null
                        )
                );

        mvc.perform(
                        assignSaleCustomer(
                                opened.id(),
                                fixture.customer()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.customerId")
                                .value(
                                        fixture.customer().toString()
                                )
                );
    }

    @Test
    void replacesCustomerOnOpenSale()
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
                                        "Cliente alterno",
                                        null,
                                        null,
                                        true,
                                        false,
                                        false
                                )
                        ).getId()
                );

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Cambio cliente",
                                fixture.customer()
                        )
                );

        mvc.perform(
                        assignSaleCustomer(
                                opened.id(),
                                otherCustomer
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.customerId")
                                .value(
                                        otherCustomer.toString()
                                )
                );
    }

    @Test
    void removesCustomerFromOpenSale()
            throws Exception {

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Quitar cliente",
                                fixture.customer()
                        )
                );

        mvc.perform(
                        authenticated(
                                delete(
                                        "/api/v1/sales/{saleId}/customer",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.customerId")
                                .doesNotExist()
                );
    }

    @Test
    void rejectsCustomerFromAnotherBusiness()
            throws Exception {

        UUID foreignCustomer =
                transactions.execute(status -> {

                    Business otherBusiness =
                            businesses.save(
                                    new Business(
                                            "Otro negocio",
                                            null,
                                            "MXN",
                                            "America/Mexico_City"
                                    )
                            );

                    return customers.save(
                            new Customer(
                                    otherBusiness,
                                    "Cliente externo",
                                    null,
                                    null,
                                    true,
                                    false,
                                    false
                            )
                    ).getId();
                });

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Cliente externo",
                                null
                        )
                );

        mvc.perform(
                        assignSaleCustomer(
                                opened.id(),
                                foreignCustomer
                        )
                )
                .andExpect(status().isNotFound());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.customerId")
                                .doesNotExist()
                );
    }

    @Test
    void cancelsOpenSaleThroughHttp() throws Exception {
        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Cuenta para cancelar",
                                null
                        )
                );

        mvc.perform(
                        authenticated(
                                post(
                                        "/api/v1/sales/{saleId}/cancel",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "reason": "Cliente canceló el pedido"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED")
                );

        Sale cancelled =
                sales.findById(opened.id())
                        .orElseThrow();

        assertThat(cancelled.getStatus())
                .isEqualTo(SaleStatus.CANCELLED);

        Map<String, Object> event =
                jdbc.queryForMap(
                        """
                        select event_type, details
                        from sale_event
                        where sale_id = ?
                          and event_type = 'SALE_CANCELLED'
                        """,
                        opened.id()
                );

        assertThat(event.get("event_type"))
                .isEqualTo("SALE_CANCELLED");

        String details =
                event.get("details").toString();

        assertThat(details)
                .contains("Cliente canceló el pedido");

        mvc.perform(
                        authenticated(
                                get("/api/v1/sales/open"),
                                fixture.phone()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$[?(@.id == '%s')]"
                                        .formatted(opened.id())
                        ).isEmpty()
                );
    }

    @Test
    void rejectsCancellingAlreadyCancelledSale()
            throws Exception {

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Cancelar dos veces",
                                null
                        )
                );

        String body = """
        {
          "reason": "Cliente canceló"
        }
        """;

        mvc.perform(
                        authenticated(
                                post(
                                        "/api/v1/sales/{saleId}/cancel",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk());

        mvc.perform(
                        authenticated(
                                post(
                                        "/api/v1/sales/{saleId}/cancel",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsCancellationWithoutReason()
            throws Exception {

        SaleResponse opened =
                service.open(
                        fixture.user(),
                        fixture.business(),
                        fixture.terminal(),
                        new OpenSaleRequest(
                                fixture.session(),
                                "Sin motivo",
                                null
                        )
                );

        mvc.perform(
                        authenticated(
                                post(
                                        "/api/v1/sales/{saleId}/cancel",
                                        opened.id()
                                ),
                                fixture.terminal()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "reason": ""
                                }
                                """)
                )
                .andExpect(status().isBadRequest());
    }

    private int accountCount() {
        return jdbc.queryForObject("select count(*) from sale where cash_session_id = ?",
                Integer.class, fixture.session());
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

    private MockHttpServletRequestBuilder assignSaleCustomer(
            UUID saleId,
            UUID customerId
    ) {
        String body = """
            {
              "customerId": "%s"
            }
            """.formatted(customerId);

        return authenticated(
                patch(
                        "/api/v1/sales/{saleId}/customer",
                        saleId
                ),
                fixture.terminal()
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
