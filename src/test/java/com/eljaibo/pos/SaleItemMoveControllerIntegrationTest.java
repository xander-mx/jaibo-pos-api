package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.catalog.domain.Category;
import com.eljaibo.pos.catalog.domain.InventoryMode;
import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.domain.ProductPresentation;
import com.eljaibo.pos.catalog.domain.Unit;
import com.eljaibo.pos.catalog.persistence.CategoryRepository;
import com.eljaibo.pos.catalog.persistence.ProductPresentationRepository;
import com.eljaibo.pos.catalog.persistence.ProductRepository;
import com.eljaibo.pos.catalog.persistence.UnitRepository;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.persistence.OperatingCycleRepository;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.response.SaleResponse;
import com.eljaibo.pos.sale.application.SaleService;
import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleItem;
import com.eljaibo.pos.sale.persistence.SaleItemRepository;
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
import java.time.Instant;
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
class SaleItemMoveControllerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    BusinessRepository businesses;
    @Autowired
    BranchRepository branches;
    @Autowired
    AppUserRepository users;
    @Autowired
    TerminalRepository terminals;
    @Autowired
    OperatingCycleRepository cycles;
    @Autowired
    CashSessionRepository sessions;
    @Autowired
    CustomerRepository customers;
    @Autowired
    SaleRepository sales;
    @Autowired
    SaleItemRepository saleItems;
    @Autowired
    CategoryRepository categories;
    @Autowired
    UnitRepository units;
    @Autowired
    ProductRepository products;
    @Autowired
    ProductPresentationRepository presentations;
    @Autowired
    SaleService saleService;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    JdbcTemplate jdbc;

    private Fixture fixture;
    private CatalogFixture catalog;

    private record Fixture(
            UUID business,
            UUID user,
            UUID terminal,
            UUID session,
            UUID customer
    ) {
    }

    private record CatalogFixture(
            UUID presentation
    ) {
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        catalog = createCatalog();
    }

    @Test
    void movesItemToAnotherOpenSale()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("2"),
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        moveItem(
                                sourceSale,
                                itemId,
                                targetSale
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(sourceSale.toString())
                )
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(0.00)
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(itemId.toString())
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.items[0].unitPrice")
                                .value(100.00)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(200.00)
                );
    }

    @Test
    void recalculatesSourceAndTargetTotals()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID movedItem =
                createItem(
                        sourceSale,
                        new BigDecimal("2"),
                        new BigDecimal("100.00")
                );

        createItem(
                sourceSale,
                BigDecimal.ONE,
                new BigDecimal("50.00")
        );

        createItem(
                targetSale,
                BigDecimal.ONE,
                new BigDecimal("30.00")
        );

        mvc.perform(
                        moveItem(
                                sourceSale,
                                movedItem,
                                targetSale
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.total")
                                .value(50.00)
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.total")
                                .value(230.00)
                )
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(2)
                );
    }

    @Test
    void preservesItemCommercialStateWhenMoved()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        BigDecimal.ONE,
                        new BigDecimal("200.00")
                );

        transactions.executeWithoutResult(status -> {
            SaleItem item =
                    saleItems.findById(itemId)
                            .orElseThrow();

            item.applyDiscount(
                    DiscountType.PERCENT,
                    new BigDecimal("10")
            );

            Sale source =
                    sales.findById(sourceSale)
                            .orElseThrow();

            source.refreshTotals(
                    item.getGrossAmount(),
                    item.getDiscountAmount(),
                    BigDecimal.ZERO
            );
        });

        mvc.perform(
                        moveItem(
                                sourceSale,
                                itemId,
                                targetSale
                        )
                )
                .andExpect(status().isOk());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].unitPrice")
                                .value(200.00)
                )
                .andExpect(
                        jsonPath("$.items[0].discountType")
                                .value("PERCENT")
                )
                .andExpect(
                        jsonPath("$.items[0].discountValue")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.items[0].discountAmount")
                                .value(20.00)
                )
                .andExpect(
                        jsonPath("$.items[0].lineTotal")
                                .value(180.00)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(180.00)
                );
    }

    @Test
    void rejectsMoveToSameSale()
            throws Exception {

        UUID saleId =
                createOpenSale("Origen");

        UUID itemId =
                createItem(
                        saleId,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        moveItem(
                                saleId,
                                itemId,
                                saleId
                        )
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select sale_id
                        from sale_item
                        where id = ?
                        """,
                        UUID.class,
                        itemId
                )
        ).isEqualTo(saleId);
    }

    @Test
    void rejectsMoveToClosedSale()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                );

        transactions.executeWithoutResult(status -> {
            Sale target =
                    sales.findById(targetSale)
                            .orElseThrow();

            target.markPaid(
                    users.findById(
                            fixture.user()
                    ).orElseThrow(),
                    terminals.findById(
                            fixture.terminal()
                    ).orElseThrow(),
                    Instant.now()
            );
        });

        mvc.perform(
                        moveItem(
                                sourceSale,
                                itemId,
                                targetSale
                        )
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select sale_id
                        from sale_item
                        where id = ?
                        """,
                        UUID.class,
                        itemId
                )
        ).isEqualTo(sourceSale);
    }

    @Test
    void rejectsMoveToSaleFromAnotherBusiness()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID itemId =
                createItem(
                        sourceSale,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                );

        Fixture other =
                createFixture();

        SaleResponse foreignSale =
                saleService.open(
                        other.user(),
                        other.business(),
                        other.terminal(),
                        new OpenSaleRequest(
                                other.session(),
                                "Venta externa",
                                null
                        )
                );

        mvc.perform(
                        moveItem(
                                sourceSale,
                                itemId,
                                foreignSale.id()
                        )
                )
                .andExpect(status().isNotFound());

        assertThat(
                jdbc.queryForObject(
                        """
                        select sale_id
                        from sale_item
                        where id = ?
                        """,
                        UUID.class,
                        itemId
                )
        ).isEqualTo(sourceSale);
    }

    @Test
    void createsMoveEventsForSourceAndTarget()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        moveItem(
                                sourceSale,
                                itemId,
                                targetSale
                        )
                )
                .andExpect(status().isOk());

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_event
                        where event_type = 'SALE_ITEM_MOVED'
                          and sale_item_id = ?
                        """,
                        Integer.class,
                        itemId
                )
        ).isEqualTo(2);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_event
                        where event_type = 'SALE_ITEM_MOVED'
                          and sale_id = ?
                        """,
                        Integer.class,
                        sourceSale
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_event
                        where event_type = 'SALE_ITEM_MOVED'
                          and sale_id = ?
                        """,
                        Integer.class,
                        targetSale
                )
        ).isEqualTo(1);
    }

    @Test
    void splitsItemToAnotherOpenSale()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("2")
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(300.00)
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(200.00)
                );
    }

    @Test
    void preservesPercentageDiscountWhenSplitting()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        transactions.executeWithoutResult(status -> {

            SaleItem item =
                    saleItems.findById(itemId)
                            .orElseThrow();

            item.applyDiscount(
                    DiscountType.PERCENT,
                    new BigDecimal("10")
            );

            Sale source =
                    sales.findById(sourceSale)
                            .orElseThrow();

            source.refreshTotals(
                    item.getGrossAmount(),
                    item.getDiscountAmount(),
                    BigDecimal.ZERO
            );
        });

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("2")
                        )
                )
                .andExpect(status().isOk());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        sourceSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.items[0].discountType")
                                .value("PERCENT")
                )
                .andExpect(
                        jsonPath("$.items[0].discountValue")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.items[0].discountAmount")
                                .value(30.00)
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.items[0].discountType")
                                .value("PERCENT")
                )
                .andExpect(
                        jsonPath("$.items[0].discountValue")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.items[0].discountAmount")
                                .value(20.00)
                );
    }

    @Test
    void preservesCourtesyWhenSplitting()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        transactions.executeWithoutResult(status -> {

            SaleItem item =
                    saleItems.findById(itemId)
                            .orElseThrow();

            item.markCourtesy();

            Sale source =
                    sales.findById(sourceSale)
                            .orElseThrow();

            source.refreshTotals(
                    item.getGrossAmount(),
                    BigDecimal.ZERO,
                    item.getGrossAmount()
            );
        });

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("2")
                        )
                )
                .andExpect(status().isOk());

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        sourceSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].courtesy")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(0)
                );

        mvc.perform(
                        authenticated(
                                get(
                                        "/api/v1/sales/{saleId}",
                                        targetSale
                                ),
                                fixture.terminal()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].courtesy")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.total")
                                .value(0)
                );
    }

    @Test
    void rejectsPartialMoveWithFixedDiscount()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        transactions.executeWithoutResult(status -> {

            SaleItem item =
                    saleItems.findById(itemId)
                            .orElseThrow();

            item.applyDiscount(
                    DiscountType.AMOUNT,
                    new BigDecimal("50.00")
            );
        });

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("2")
                        )
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbc.queryForObject(
                        """
                        select quantity
                        from sale_item
                        where id = ?
                        """,
                        BigDecimal.class,
                        itemId
                )
        ).isEqualByComparingTo("5.000");
    }

    @Test
    void rejectsSplitQuantityEqualOrGreaterThanItemQuantity()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID itemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("5")
                        )
                )
                .andExpect(status().isConflict());

        mvc.perform(
                        splitItem(
                                sourceSale,
                                itemId,
                                targetSale,
                                new BigDecimal("6")
                        )
                )
                .andExpect(status().isConflict());
    }

    @Test
    void createsPartialMoveEventsForSourceAndTarget()
            throws Exception {

        UUID sourceSale =
                createOpenSale("Origen");

        UUID targetSale =
                createOpenSale("Destino");

        UUID sourceItemId =
                createItem(
                        sourceSale,
                        new BigDecimal("5"),
                        new BigDecimal("100.00")
                );

        mvc.perform(
                        splitItem(
                                sourceSale,
                                sourceItemId,
                                targetSale,
                                new BigDecimal("2")
                        )
                )
                .andExpect(status().isOk());

        Integer count =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from sale_event
                        where event_type = 'SALE_ITEM_MOVED'
                          and sale_id in (?, ?)
                        """,
                        Integer.class,
                        sourceSale,
                        targetSale
                );

        assertThat(count).isEqualTo(2);

        List<String> modes =
                jdbc.queryForList(
                        """
                        select details ->> 'mode'
                        from sale_event
                        where event_type = 'SALE_ITEM_MOVED'
                          and sale_id in (?, ?)
                        order by sale_id
                        """,
                        String.class,
                        sourceSale,
                        targetSale
                );

        assertThat(modes)
                .containsExactlyInAnyOrder(
                        "PARTIAL",
                        "PARTIAL"
                );
    }

    private MockHttpServletRequestBuilder splitItem(
            UUID sourceSaleId,
            UUID itemId,
            UUID targetSaleId,
            BigDecimal quantity
    ) {
        String body = """
            {
              "targetSaleId": "%s",
              "quantity": %s
            }
            """.formatted(
                targetSaleId,
                quantity.toPlainString()
        );

        return authenticated(
                post(
                        "/api/v1/sales/{saleId}/items/{itemId}/split",
                        sourceSaleId,
                        itemId
                ),
                fixture.terminal()
        )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .content(body);
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
                                    new BigDecimal("500.00"),
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

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UUID terminal) {
        return request.with(jwt().jwt(token -> token.subject("cashier")
                        .claim("user_id", fixture.user().toString())
                        .claim("business_id", fixture.business().toString())
                        .claim("roles", List.of("CASHIER"))))
                .header("X-Terminal-Id", terminal);
    }

    private CatalogFixture createCatalog() {

        return transactions.execute(status -> {

            Business business =
                    businesses.findById(
                            fixture.business()
                    ).orElseThrow();

            Category category =
                    categories.save(
                            new Category(
                                    business,
                                    "Mariscos",
                                    0
                            )
                    );

            String unitCode =
                    "PZ"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8);

            Unit unit =
                    units.save(
                            new Unit(
                                    unitCode,
                                    "Pieza",
                                    "pz",
                                    0
                            )
                    );

            Product product =
                    products.save(
                            new Product(
                                    business,
                                    category,
                                    null,
                                    "Producto prueba",
                                    null,
                                    InventoryMode.NOT_TRACKED,
                                    true,
                                    false,
                                    0
                            )
                    );

            ProductPresentation presentation =
                    presentations.save(
                            new ProductPresentation(
                                    product,
                                    unit,
                                    "Orden",
                                    BigDecimal.ONE,
                                    new BigDecimal("100.00"),
                                    false,
                                    null,
                                    false,
                                    0,
                                    BigDecimal.ONE
                            )
                    );

            return new CatalogFixture(
                    presentation.getId()
            );
        });
    }

    private UUID createOpenSale(
            String reference
    ) {
        return saleService.open(
                fixture.user(),
                fixture.business(),
                fixture.terminal(),
                new OpenSaleRequest(
                        fixture.session(),
                        reference,
                        null
                )
        ).id();
    }

    private UUID createItem(
            UUID saleId,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
        return transactions.execute(status -> {

            Sale sale =
                    sales.findById(saleId)
                            .orElseThrow();

            ProductPresentation presentation =
                    presentations.findById(
                            catalog.presentation()
                    ).orElseThrow();

            AppUser user =
                    users.findById(
                            fixture.user()
                    ).orElseThrow();

            Terminal terminal =
                    terminals.findById(
                            fixture.terminal()
                    ).orElseThrow();

            SaleItem item =
                    saleItems.save(
                            new SaleItem(
                                    sale,
                                    presentation,
                                    quantity,
                                    unitPrice,
                                    user,
                                    terminal
                            )
                    );

            List<SaleItem> currentItems =
                    saleItems
                            .findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
                                    saleId
                            );

            BigDecimal subtotal =
                    currentItems.stream()
                            .map(SaleItem::getGrossAmount)
                            .reduce(
                                    BigDecimal.ZERO,
                                    BigDecimal::add
                            );

            BigDecimal discounts =
                    currentItems.stream()
                            .map(SaleItem::getDiscountAmount)
                            .reduce(
                                    BigDecimal.ZERO,
                                    BigDecimal::add
                            );

            BigDecimal courtesies =
                    currentItems.stream()
                            .filter(SaleItem::isCourtesy)
                            .map(SaleItem::getGrossAmount)
                            .reduce(
                                    BigDecimal.ZERO,
                                    BigDecimal::add
                            );

            sale.refreshTotals(
                    subtotal,
                    discounts,
                    courtesies
            );

            return item.getId();
        });
    }

    private MockHttpServletRequestBuilder moveItem(
            UUID sourceSaleId,
            UUID itemId,
            UUID targetSaleId
    ) {
        String body = """
            {
              "targetSaleId": "%s"
            }
            """.formatted(
                targetSaleId
        );

        return authenticated(
                post(
                        "/api/v1/sales/{saleId}/items/{itemId}/move",
                        sourceSaleId,
                        itemId
                ),
                fixture.terminal()
        )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .content(body);
    }
}