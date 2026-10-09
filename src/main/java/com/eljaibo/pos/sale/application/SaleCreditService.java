package com.eljaibo.pos.sale.application;

import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.inventory.application.InventoryService;
import com.eljaibo.pos.realtime.sale.SaleRealtimePublisher;
import com.eljaibo.pos.sale.api.request.CloseCreditSaleRequest;
import com.eljaibo.pos.sale.api.request.PaymentRequest;
import com.eljaibo.pos.sale.api.response.SaleDetailResponse;
import com.eljaibo.pos.sale.api.response.SaleItemResponse;
import com.eljaibo.pos.sale.domain.Receivable;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleEvent;
import com.eljaibo.pos.sale.domain.SalePayment;
import com.eljaibo.pos.sale.domain.SaleStatus;
import com.eljaibo.pos.sale.persistence.ReceivableRepository;
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
public class SaleCreditService {

    private static final Logger log =
            LoggerFactory.getLogger(SaleCreditService.class);

    private final SaleRepository sales;
    private final ReceivableRepository receivables;
    private final SaleEventRepository events;
    private final AppUserRepository users;
    private final TerminalRepository terminals;
    private final CashSessionRepository sessions;
    private final SaleItemRepository saleItems;
    private final SalePaymentRepository payments;
    private final SaleRealtimePublisher saleRealtimePublisher;
    private final InventoryService inventoryService;
    private final Clock clock;

    public SaleCreditService(
            SaleRepository sales,
            ReceivableRepository receivables,
            SaleEventRepository events,
            AppUserRepository users,
            TerminalRepository terminals,
            CashSessionRepository sessions,
            SaleItemRepository saleItems,
            SalePaymentRepository payments,
            SaleRealtimePublisher saleRealtimePublisher,
            InventoryService inventoryService,
            Clock clock
    ) {
        this.sales = sales;
        this.receivables = receivables;
        this.events = events;
        this.users = users;
        this.terminals = terminals;
        this.sessions = sessions;
        this.saleItems = saleItems;
        this.payments = payments;
        this.saleRealtimePublisher = saleRealtimePublisher;
        this.inventoryService = inventoryService;
        this.clock = clock;
    }

    @Transactional
    public SaleDetailResponse closeAsCredit(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID saleId,
            CloseCreditSaleRequest request
    ) {
        log.info(
                "Starting credit sale closing. saleId={}, businessId={}, terminalId={}, userId={}",
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

        Sale sale = findOpenSale(
                saleId,
                businessId,
                terminal
        );

        if (sale.getCustomer() == null) {
            log.warn(
                    "Credit sale rejected because customer is missing. saleId={}",
                    saleId
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A customer is required for a credit sale"
            );
        }

        if (sale.getTotal().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Credit sale total must be greater than zero"
            );
        }

        List<PaymentRequest> initialPayments =
                request == null || request.payments() == null
                        ? List.of()
                        : request.payments();

        BigDecimal paidTotal =
                initialPayments
                        .stream()
                        .map(PaymentRequest::amount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (paidTotal.compareTo(sale.getTotal()) > 0) {
            log.warn(
                    "Credit sale rejected because initial payments exceed total. saleId={}, total={}, paidTotal={}",
                    saleId,
                    sale.getTotal(),
                    paidTotal
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Initial payments cannot exceed sale total"
            );
        }

        BigDecimal balance =
                sale.getTotal()
                        .subtract(paidTotal)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (receivables.findBySaleId(saleId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sale already has a receivable"
            );
        }

        Instant now = clock.instant();

        List<SalePayment> salePayments =
                initialPayments
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

        if (!salePayments.isEmpty()) {
            payments.saveAll(salePayments);
        }

        if (balance.signum() > 0) {

            sale.markCredit(
                    user,
                    terminal,
                    now
            );

            Receivable receivable =
                    new Receivable(
                            sale,
                            balance,
                            now
                    );

            receivables.save(receivable);

            events.save(
                    SaleEvent.credit(
                            sale,
                            user,
                            terminal,
                            now
                    )
            );

            log.info(
                    "Sale closed as credit. saleId={}, total={}, paidTotal={}, balance={}, customerId={}",
                    sale.getId(),
                    sale.getTotal(),
                    paidTotal,
                    balance,
                    sale.getCustomer().getId()
            );

        } else {

            sale.markPaid(
                    user,
                    terminal,
                    now
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
                    "Credit closing resulted in fully paid sale. saleId={}, total={}",
                    sale.getId(),
                    sale.getTotal()
            );
        }

        inventoryService.consumeSale(
                sale,
                user,
                terminal
        );

        saleRealtimePublisher.saleChanged(
                sale.getId(),
                sale.getBranch().getId()
        );

        return detailResponse(sale);
    }

    private AppUser findUser(
            UUID userId,
            UUID businessId
    ) {
        AppUser user = users.findById(userId)
                .orElseThrow(
                        () -> notFound("User not found")
                );

        if (!user.getBusiness().getId().equals(businessId)
                || !user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid user"
            );
        }

        return user;
    }

    private Terminal findTerminal(
            UUID terminalId,
            UUID businessId
    ) {
        Terminal terminal = terminals.findById(terminalId)
                .orElseThrow(
                        () -> notFound("Terminal not found")
                );

        if (!terminal.getBranch()
                .getBusiness()
                .getId()
                .equals(businessId)) {
            throw notFound("Terminal not found");
        }

        return terminal;
    }

    private Sale findOpenSale(
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
                        () -> notFound("Cash session not found")
                );

        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        return sale;
    }

    private ResponseStatusException notFound(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
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
}