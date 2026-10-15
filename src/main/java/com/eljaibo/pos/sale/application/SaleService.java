package com.eljaibo.pos.sale.application;

import com.eljaibo.pos.cash.domain.CashMovement;
import com.eljaibo.pos.cash.domain.CashMovementDirection;
import com.eljaibo.pos.cash.domain.CashMovementType;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashMovementRepository;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.catalog.domain.ProductPresentation;
import com.eljaibo.pos.catalog.persistence.ProductPresentationRepository;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.inventory.application.InventoryService;
import com.eljaibo.pos.realtime.sale.SaleRealtimePublisher;
import com.eljaibo.pos.sale.api.request.AddSaleItemRequest;
import com.eljaibo.pos.sale.api.request.ApplySaleDiscountRequest;
import com.eljaibo.pos.sale.api.request.ApplySaleItemDiscountRequest;
import com.eljaibo.pos.sale.api.request.AssignSaleCustomerRequest;
import com.eljaibo.pos.sale.api.request.CancelSaleRequest;
import com.eljaibo.pos.sale.api.request.ChangeSaleItemPriceRequest;
import com.eljaibo.pos.sale.api.request.ChangeSaleItemQuantityRequest;
import com.eljaibo.pos.sale.api.request.MoveSaleItemRequest;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.request.PaySaleRequest;
import com.eljaibo.pos.sale.api.request.SplitSaleItemRequest;
import com.eljaibo.pos.sale.api.response.SaleDetailResponse;
import com.eljaibo.pos.sale.api.response.SaleItemResponse;
import com.eljaibo.pos.sale.api.response.SaleResponse;
import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleEvent;
import com.eljaibo.pos.sale.domain.SaleItem;
import com.eljaibo.pos.sale.domain.SalePayment;
import com.eljaibo.pos.sale.domain.SaleStatus;
import com.eljaibo.pos.sale.persistence.SaleEventRepository;
import com.eljaibo.pos.sale.persistence.SaleItemRepository;
import com.eljaibo.pos.sale.persistence.SalePaymentRepository;
import com.eljaibo.pos.sale.persistence.SaleRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SaleService {
    private final SaleRepository sales;
    private final SaleEventRepository events;
    private final CashSessionRepository sessions;
    private final AppUserRepository users;
    private final TerminalRepository terminals;
    private final CustomerRepository customers;
    private final Clock clock;
    private final SaleItemRepository saleItems;
    private final ProductPresentationRepository presentations;
    private final SaleRealtimePublisher saleRealtimePublisher;
    private final SalePaymentRepository payments;
    private final CashMovementRepository cashMovements;
    private final InventoryService inventoryService;

    private static final Logger log =
            LoggerFactory.getLogger(SaleService.class);

    public SaleService(SaleRepository sales, SaleEventRepository events,
                       CashSessionRepository sessions, AppUserRepository users,
                       TerminalRepository terminals, CustomerRepository customers, Clock clock,
                       SaleItemRepository saleItems, ProductPresentationRepository presentations,
                       SaleRealtimePublisher saleRealtimePublisher, SalePaymentRepository payments,
                       CashMovementRepository cashMovements, InventoryService inventoryService) {
        this.sales = sales;
        this.events = events;
        this.sessions = sessions;
        this.users = users;
        this.terminals = terminals;
        this.customers = customers;
        this.clock = clock;
        this.saleItems = saleItems;
        this.presentations = presentations;
        this.saleRealtimePublisher = saleRealtimePublisher;
        this.payments = payments;
        this.cashMovements = cashMovements;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public SaleResponse open(UUID userId, UUID businessId, UUID terminalId, OpenSaleRequest request) {
        AppUser user = findUser(userId, businessId);
        Terminal terminal = findTerminal(terminalId, businessId);
        // Serializes numbering and opening against other opens and cash-session closing.
        CashSession session = sessions.findByIdAndOperatingCycleBranchBusinessId(
                        request.cashSessionId(), businessId)
                .filter(value -> value.getOperatingCycle().getBranch().getId()
                        .equals(terminal.getBranch().getId()))
                .orElseThrow(() -> notFound("Cash session not found"));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An open cash session is required");
        }
        int lastNumber = sales.findLastSaleNumber(session.getId());
        if (lastNumber == Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sale number limit reached");
        }
        Sale sale = new Sale(session, lastNumber + 1, request.reference(), user, terminal);
        if (request.customerId() != null) {
            sale.assignCustomer(customers.findById(request.customerId())
                    .filter(value -> value.isActive() && value.getBusiness().getId().equals(businessId))
                    .orElseThrow(() -> notFound("Active customer not found")));
        }
        sales.save(sale);
        events.save(SaleEvent.opened(sale, user, terminal, clock.instant()));
        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );
        return SaleResponse.from(sale);
    }

    @Transactional
    public SaleDetailResponse cancel(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            CancelSaleRequest request
    ) {
        log.info(
                "Starting sale cancellation. saleId={}, businessId={}, terminalId={}, userId={}",
                saleId,
                businessId,
                terminalId,
                userId
        );

        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = findOpenSaleForModification(
                saleId,
                businessId,
                terminal
        );

        Instant now = clock.instant();

        try {
            sale.cancel(
                    user,
                    terminal,
                    now
            );
        } catch (
                IllegalArgumentException |
                IllegalStateException ex
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        events.save(
                SaleEvent.cancelled(
                        sale,
                        request.reason(),
                        user,
                        terminal,
                        now
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        log.info(
                "Sale cancelled successfully. saleId={}, total={}",
                sale.getId(),
                sale.getTotal()
        );

        return detailResponse(sale);
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> findOpen(UUID userId, UUID businessId, UUID terminalId) {
        findUser(userId, businessId);
        Terminal terminal = findTerminal(terminalId, businessId);
        return sales.findAllByBusinessIdAndBranchIdAndStatusOrderByCreatedAtAscSaleNumberAsc(
                        businessId, terminal.getBranch().getId(), SaleStatus.OPEN)
                .stream().map(SaleResponse::from).toList();
    }

    @Transactional
    public SaleDetailResponse addItem(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            AddSaleItemRequest request
    ) {
        AppUser user = findUser(userId, businessId);

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = sales
                .findByIdAndBusinessIdAndBranchId(
                        saleId,
                        businessId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(
                        () -> notFound("Open sale not found")
                );

        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open sale can be modified"
            );
        }

        CashSession session = sessions
                .findByIdAndOperatingCycleBranchBusinessId(
                        sale.getCashSession().getId(),
                        businessId
                )
                .orElseThrow(
                        () -> notFound("Cash session not found")
                );

        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        ProductPresentation presentation =
                presentations
                        .findByIdAndProductBusinessId(
                                request.presentationId(),
                                businessId
                        )
                        .filter(ProductPresentation::isActive)
                        .orElseThrow(
                                () -> notFound(
                                        "Active presentation not found"
                                )
                        );

        BigDecimal unitPrice = resolvePrice(
                presentation,
                request.unitPrice()
        );

        SaleItem item;

        try {
            item = new SaleItem(
                    sale,
                    presentation,
                    request.quantity(),
                    unitPrice,
                    user,
                    terminal
            );
        } catch (IllegalArgumentException
                 | IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        saleItems.save(item);

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemAdded(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional(readOnly = true)
    public SaleDetailResponse findById(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId
    ) {
        findUser(userId, businessId);

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = sales
                .findById(saleId)
                .filter(value ->
                        value.getBusiness().getId().equals(businessId)
                                &&
                                value.getBranch().getId()
                                        .equals(terminal.getBranch().getId())
                )
                .orElseThrow(
                        () -> notFound("Sale not found")
                );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse changeItemQuantity(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId,
            ChangeSaleItemQuantityRequest request
    ) {
        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        /*
         * Bloqueamos la cuenta.
         *
         * Todas las modificaciones de partidas de esta venta
         * pasan primero por este lock.
         */
        Sale sale = sales
                .findByIdAndBusinessIdAndBranchId(
                        saleId,
                        businessId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(
                        () -> notFound("Sale not found")
                );

        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open sale can be modified"
            );
        }

        CashSession session = sessions
                .findByIdAndOperatingCycleBranchBusinessId(
                        sale.getCashSession().getId(),
                        businessId
                )
                .orElseThrow(
                        () -> notFound(
                                "Cash session not found"
                        )
                );

        if (session.getStatus()
                != CashSessionStatus.OPEN) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound("Sale item not found")
                );

        BigDecimal previousQuantity =
                item.getQuantity();

        try {

            item.changeQuantity(
                    request.quantity()
            );

        } catch (IllegalArgumentException
                 | IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemQuantityChanged(
                        sale,
                        item,
                        previousQuantity,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse removeItem(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId
    ) {
        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = sales
                .findByIdAndBusinessIdAndBranchId(
                        saleId,
                        businessId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(
                        () -> notFound("Sale not found")
                );

        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open sale can be modified"
            );
        }

        CashSession session = sessions
                .findByIdAndOperatingCycleBranchBusinessId(
                        sale.getCashSession().getId(),
                        businessId
                )
                .orElseThrow(
                        () -> notFound("Cash session not found")
                );

        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound("Sale item not found")
                );

        try {
            item.remove(
                    user,
                    terminal
            );
        } catch (IllegalArgumentException
                 | IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        /*
         * refreshSaleTotals solo toma partidas con
         * removed = false, por lo que la partida eliminada
         * deja de participar automáticamente.
         */
        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemRemoved(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse changeItemPrice(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId,
            ChangeSaleItemPriceRequest request
    ) {
        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = sales
                .findByIdAndBusinessIdAndBranchId(
                        saleId,
                        businessId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(
                        () -> notFound("Sale not found")
                );

        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open sale can be modified"
            );
        }

        CashSession session = sessions
                .findByIdAndOperatingCycleBranchBusinessId(
                        sale.getCashSession().getId(),
                        businessId
                )
                .orElseThrow(
                        () -> notFound("Cash session not found")
                );

        if (session.getStatus()
                != CashSessionStatus.OPEN) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound("Sale item not found")
                );

        BigDecimal previousPrice =
                item.getUnitPrice();

        try {

            item.changePrice(
                    request.unitPrice()
            );

        } catch (IllegalArgumentException
                 | IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemPriceChanged(
                        sale,
                        item,
                        previousPrice,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse applyItemDiscount(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId,
            ApplySaleItemDiscountRequest request
    ) {
        AppUser user = findUser(userId, businessId);
        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = findOpenSaleForModification(
                saleId,
                businessId,
                terminal
        );

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound(
                                "Sale item not found"
                        )
                );

        try {
            item.applyDiscount(
                    request.discountType(),
                    request.discountValue()
            );
        } catch (
                IllegalArgumentException |
                IllegalStateException ex
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemDiscountApplied(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse removeItemDiscount(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId
    ) {
        AppUser user =
                findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Sale sale =
                findOpenSaleForModification(
                        saleId,
                        businessId,
                        terminal
                );

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound(
                                "Sale item not found"
                        )
                );

        item.removeDiscount();

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemDiscountRemoved(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse markItemCourtesy(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId
    ) {
        AppUser user =
                findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Sale sale =
                findOpenSaleForModification(
                        saleId,
                        businessId,
                        terminal
                );

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound(
                                "Sale item not found"
                        )
                );

        item.markCourtesy();

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemCourtesyApplied(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse removeItemCourtesy(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            UUID itemId
    ) {
        AppUser user =
                findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Sale sale =
                findOpenSaleForModification(
                        saleId,
                        businessId,
                        terminal
                );

        SaleItem item = saleItems
                .findByIdAndSaleIdAndRemovedFalse(
                        itemId,
                        saleId
                )
                .orElseThrow(
                        () -> notFound(
                                "Sale item not found"
                        )
                );

        item.removeCourtesy();

        refreshSaleTotals(sale);

        events.save(
                SaleEvent.itemCourtesyRemoved(
                        sale,
                        item,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse applySaleDiscount(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            ApplySaleDiscountRequest request
    ) {
        AppUser user = findUser(userId, businessId);

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = findOpenSaleForModification(
                saleId,
                businessId,
                terminal
        );

        try {
            sale.applySaleDiscount(
                    request.discountType(),
                    request.discountValue()
            );
        } catch (
                IllegalArgumentException |
                IllegalStateException ex
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        events.save(
                SaleEvent.saleDiscountApplied(
                        sale,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse removeSaleDiscount(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId
    ) {
        AppUser user = findUser(userId, businessId);

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = findOpenSaleForModification(
                saleId,
                businessId,
                terminal
        );

        DiscountType previousDiscountType =
                sale.getSaleDiscountType();

        BigDecimal previousDiscountValue =
                sale.getSaleDiscountValue();

        BigDecimal previousDiscountAmount =
                sale.getSaleDiscountAmount();

        sale.removeSaleDiscount();

        events.save(
                SaleEvent.saleDiscountRemoved(
                        sale,
                        previousDiscountType,
                        previousDiscountValue,
                        previousDiscountAmount,
                        user,
                        terminal,
                        clock.instant()
                )
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse pay(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            PaySaleRequest request
    ) {
        log.info(
                "Starting sale payment. saleId={}, businessId={}, terminalId={}, userId={}",
                saleId,
                businessId,
                terminalId,
                userId
        );

        AppUser user = findUser(userId, businessId);

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Sale sale = findOpenSaleForModification(
                saleId,
                businessId,
                terminal
        );

        log.debug(
                "Sale found for payment. saleId={}, status={}, total={}",
                sale.getId(),
                sale.getStatus(),
                sale.getTotal()
        );

        BigDecimal paidTotal = request.payments()
                .stream()
                .map(payment -> payment.amount())
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                )
                .setScale(2, RoundingMode.HALF_UP);

        if (paidTotal.compareTo(sale.getTotal()) != 0) {
            log.warn(
                    "Payment total mismatch. saleId={}, paidTotal={}, expectedTotal={}",
                    saleId,
                    paidTotal,
                    sale.getTotal()
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment total must match sale total"
            );
        }

        Instant now = clock.instant();

        List<SalePayment> salePayments =
                request.payments()
                        .stream()
                        .map(payment ->
                                new SalePayment(
                                        sale,
                                        payment.paymentMethod(),
                                        payment.amount(),
                                        user,
                                        terminal,
                                        now
                                )
                        )
                        .toList();

        payments.saveAll(salePayments);

        request.payments()
                .stream()
                .filter(payment ->
                        payment.paymentMethod() == PaymentMethod.CASH
                )
                .forEach(payment ->
                        cashMovements.save(
                                new CashMovement(
                                        sale.getCashSession(),
                                        CashMovementDirection.IN,
                                        CashMovementType.SALE_PAYMENT,
                                        payment.amount(),
                                        "SALE",
                                        sale.getId(),
                                        "Pago en efectivo de venta",
                                        user,
                                        terminal
                                )
                        )
                );

        sale.markPaid(
                user,
                terminal,
                now
        );

        inventoryService.consumeSale(
                sale,
                user,
                terminal
        );

        events.save(
                SaleEvent.paid(
                        sale,
                        user,
                        terminal,
                        now
                )
        );

        log.info(
                "Sale paid successfully. saleId={}, total={}, paymentCount={}",
                sale.getId(),
                sale.getTotal(),
                request.payments().size()
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse assignCustomer(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            AssignSaleCustomerRequest request
    ) {
        findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Sale sale =
                findOpenSaleForModification(
                        saleId,
                        businessId,
                        terminal
                );

        var customer =
                customers.findById(request.customerId())
                        .filter(value ->
                                value.isActive()
                                        && value.getBusiness()
                                        .getId()
                                        .equals(businessId)
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Active customer not found"
                                )
                        );

        sale.assignCustomer(customer);

        log.info(
                "Customer assigned to sale. saleId={}, customerId={}",
                saleId,
                customer.getId()
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse removeCustomer(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId
    ) {
        findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Sale sale =
                findOpenSaleForModification(
                        saleId,
                        businessId,
                        terminal
                );

        sale.removeCustomer();

        log.info(
                "Customer removed from sale. saleId={}",
                saleId
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    @Transactional
    public SaleDetailResponse moveItem(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID sourceSaleId,
            UUID itemId,
            MoveSaleItemRequest request
    ) {
        AppUser user =
                findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        UUID targetSaleId =
                request.targetSaleId();

        if (sourceSaleId.equals(targetSaleId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Source and target sale must be different"
            );
        }

        /*
         * Bloqueamos siempre en el mismo orden para reducir
         * el riesgo de deadlock si dos terminales intentan
         * mover partidas en sentidos opuestos.
         */
        UUID firstId =
                sourceSaleId.compareTo(targetSaleId) < 0
                        ? sourceSaleId
                        : targetSaleId;

        UUID secondId =
                sourceSaleId.compareTo(targetSaleId) < 0
                        ? targetSaleId
                        : sourceSaleId;

        Sale first =
                findOpenSaleForModification(
                        firstId,
                        businessId,
                        terminal
                );

        Sale second =
                findOpenSaleForModification(
                        secondId,
                        businessId,
                        terminal
                );

        Sale sourceSale =
                first.getId().equals(sourceSaleId)
                        ? first
                        : second;

        Sale targetSale =
                first.getId().equals(targetSaleId)
                        ? first
                        : second;

        SaleItem item =
                saleItems
                        .findByIdAndSaleIdAndRemovedFalse(
                                itemId,
                                sourceSaleId
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Sale item not found"
                                )
                        );

        try {
            item.moveTo(targetSale);
        } catch (
                IllegalArgumentException |
                IllegalStateException ex
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        refreshSaleTotals(sourceSale);
        refreshSaleTotals(targetSale);

        Instant now = clock.instant();

        events.save(
                SaleEvent.itemMoved(
                        sourceSale,
                        item,
                        sourceSale,
                        targetSale,
                        "OUT",
                        user,
                        terminal,
                        now
                )
        );

        events.save(
                SaleEvent.itemMoved(
                        targetSale,
                        item,
                        sourceSale,
                        targetSale,
                        "IN",
                        user,
                        terminal,
                        now
                )
        );

        log.info(
                "Sale item moved. itemId={}, sourceSaleId={}, targetSaleId={}, userId={}",
                itemId,
                sourceSaleId,
                targetSaleId,
                userId
        );

        saleRealtimePublisher.saleChanged(
                sourceSale.getId(),
                sourceSale.getBranch().getId()
        );

        saleRealtimePublisher.saleChanged(
                targetSale.getId(),
                targetSale.getBranch().getId()
        );

        return detailResponse(sourceSale);
    }

    @Transactional
    public SaleDetailResponse splitItem(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID sourceSaleId,
            UUID itemId,
            SplitSaleItemRequest request
    ) {
        AppUser user =
                findUser(
                        userId,
                        businessId
                );

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        UUID targetSaleId =
                request.targetSaleId();

        if (sourceSaleId.equals(targetSaleId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Source and target sale must be different"
            );
        }

        /*
         * Igual que en moveItem:
         * bloqueamos ambas cuentas siempre en el mismo
         * orden para evitar locks cruzados.
         */
        UUID firstId =
                sourceSaleId.compareTo(targetSaleId) < 0
                        ? sourceSaleId
                        : targetSaleId;

        UUID secondId =
                sourceSaleId.compareTo(targetSaleId) < 0
                        ? targetSaleId
                        : sourceSaleId;

        Sale first =
                findOpenSaleForModification(
                        firstId,
                        businessId,
                        terminal
                );

        Sale second =
                findOpenSaleForModification(
                        secondId,
                        businessId,
                        terminal
                );

        Sale sourceSale =
                first.getId().equals(sourceSaleId)
                        ? first
                        : second;

        Sale targetSale =
                first.getId().equals(targetSaleId)
                        ? first
                        : second;

        SaleItem sourceItem =
                saleItems
                        .findByIdAndSaleIdAndRemovedFalse(
                                itemId,
                                sourceSaleId
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Sale item not found"
                                )
                        );

        BigDecimal movedQuantity =
                request.quantity();

        SaleItem targetItem;

        try {
            targetItem =
                    sourceItem.splitTo(
                            targetSale,
                            movedQuantity,
                            user,
                            terminal
                    );

        } catch (
                IllegalArgumentException |
                IllegalStateException ex
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        /*
         * Necesitamos persistir primero para obtener
         * el id de la nueva partida y usarlo en auditoría.
         */
        saleItems.save(targetItem);
        saleItems.flush();

        refreshSaleTotals(sourceSale);
        refreshSaleTotals(targetSale);

        Instant now =
                clock.instant();

        events.save(
                SaleEvent.itemPartiallyMoved(
                        sourceSale,
                        sourceItem,
                        sourceItem,
                        targetItem,
                        sourceSale,
                        targetSale,
                        "OUT",
                        movedQuantity,
                        user,
                        terminal,
                        now
                )
        );

        events.save(
                SaleEvent.itemPartiallyMoved(
                        targetSale,
                        targetItem,
                        sourceItem,
                        targetItem,
                        sourceSale,
                        targetSale,
                        "IN",
                        movedQuantity,
                        user,
                        terminal,
                        now
                )
        );

        log.info(
                "Sale item partially moved. sourceItemId={}, targetItemId={}, quantity={}, sourceSaleId={}, targetSaleId={}, userId={}",
                sourceItem.getId(),
                targetItem.getId(),
                movedQuantity,
                sourceSaleId,
                targetSaleId,
                userId
        );

        saleRealtimePublisher.saleChanged(
                sourceSale.getId(),
                sourceSale.getBranch().getId()
        );

        saleRealtimePublisher.saleChanged(
                targetSale.getId(),
                targetSale.getBranch().getId()
        );

        return detailResponse(sourceSale);
    }

    private AppUser findUser(UUID userId, UUID businessId) {
        return users.findById(userId)
                .filter(user -> user.isActive() && user.getBusiness().getId().equals(businessId)
                        && user.getBusiness().isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active user required"));
    }

    private Terminal findTerminal(UUID terminalId, UUID businessId) {
        return terminals.findByIdAndBranchBusinessIdAndActiveTrue(terminalId, businessId)
                .filter(terminal -> terminal.getBranch().isActive())
                .orElseThrow(() -> notFound("Active terminal not found"));
    }

    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private BigDecimal resolvePrice(
            ProductPresentation presentation,
            BigDecimal requestedPrice
    ) {
        if (presentation.isOpenPrice()) {

            if (requestedPrice == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "unitPrice is required for an open price presentation"
                );
            }

            if (requestedPrice.signum() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "unitPrice cannot be negative"
                );
            }

            return requestedPrice;
        }

        if (requestedPrice != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "unitPrice must not be supplied for a fixed price presentation"
            );
        }

        if (presentation.getSalePrice() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Presentation does not have a sale price"
            );
        }

        return presentation.getSalePrice();
    }

    private void refreshSaleTotals(Sale sale) {

        List<SaleItem> activeItems =
                saleItems
                        .findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
                                sale.getId()
                        );

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal courtesies = BigDecimal.ZERO;

        for (SaleItem item : activeItems) {

            subtotal = subtotal.add(
                    item.getGrossAmount()
            );

            discounts = discounts.add(
                    item.getDiscountAmount()
            );

            if (item.isCourtesy()) {
                courtesies = courtesies.add(
                        item.getGrossAmount()
                );
            }
        }

        sale.refreshTotals(
                subtotal,
                discounts,
                courtesies
        );
    }

    private SaleDetailResponse detailResponse(Sale sale) {

        List<SaleItemResponse> items =
                saleItems
                        .findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
                                sale.getId()
                        )
                        .stream()
                        .map(SaleItemResponse::from)
                        .toList();

        return SaleDetailResponse.from(
                sale,
                items
        );
    }

    private Sale findOpenSaleForModification(
            UUID saleId,
            UUID businessId,
            Terminal terminal
    ) {
        Sale sale = sales
                .findByIdAndBusinessIdAndBranchId(
                        saleId,
                        businessId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(
                        () -> notFound("Sale not found")
                );

        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open sale can be modified"
            );
        }

        CashSession session = sessions
                .findByIdAndOperatingCycleBranchBusinessId(
                        sale.getCashSession().getId(),
                        businessId
                )
                .orElseThrow(
                        () -> notFound(
                                "Cash session not found"
                        )
                );

        if (session.getStatus()
                != CashSessionStatus.OPEN) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        return sale;
    }
}
