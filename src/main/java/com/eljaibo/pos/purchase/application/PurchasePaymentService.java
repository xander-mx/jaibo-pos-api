package com.eljaibo.pos.purchase.application;

import com.eljaibo.pos.cash.domain.*;
import com.eljaibo.pos.cash.persistence.CashMovementRepository;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.purchase.api.request.RegisterPurchasePaymentRequest;
import com.eljaibo.pos.purchase.api.response.PurchasePaymentResponse;
import com.eljaibo.pos.purchase.domain.*;
import com.eljaibo.pos.purchase.persistence.PurchasePaymentRepository;
import com.eljaibo.pos.purchase.persistence.PurchaseRepository;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class PurchasePaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PurchasePaymentService.class);

    private final PurchaseRepository purchases;
    private final PurchasePaymentRepository payments;
    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final AppUserRepository users;
    private final TerminalRepository terminals;
    private final Clock clock;

    public PurchasePaymentService(
            PurchaseRepository purchases,
            PurchasePaymentRepository payments,
            CashSessionRepository sessions,
            CashMovementRepository movements,
            AppUserRepository users,
            TerminalRepository terminals,
            Clock clock
    ) {
        this.purchases = purchases;
        this.payments = payments;
        this.sessions = sessions;
        this.movements = movements;
        this.users = users;
        this.terminals = terminals;
        this.clock = clock;
    }

    @Transactional
    public PurchasePaymentResponse register(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID purchaseId,
            RegisterPurchasePaymentRequest request
    ) {
        AppUser user = users.findById(userId)
                .filter(value ->
                        value.isActive()
                                && value.getBusiness()
                                .getId()
                                .equals(businessId)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Active user required"
                        )
                );

        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator role is required"
            );
        }

        Terminal terminal = terminals
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active terminal not found"
                        )
                );

        Purchase purchase = purchases.findForPayment(
                        purchaseId,
                        terminal.getBranch().getId()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Purchase not found"
                        )
                );

        if (purchase.getStatus() != PurchaseStatus.RECEIVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only received purchases can receive payments"
            );
        }

        BigDecimal paid = payments.sumAmountByPurchaseId(
                purchaseId
        );

        BigDecimal balance = purchase.getTotal()
                .subtract(paid);

        if (request.amount().compareTo(balance) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment exceeds purchase balance"
            );
        }

        CashSession cashSession = null;

        if (request.paymentSource()
                == PurchasePaymentSource.CASH_REGISTER) {

            if (request.paymentMethod() != PaymentMethod.CASH) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cash register payments must use cash"
                );
            }

            cashSession = sessions
                    .findOpenSessionForUpdate(
                            terminal.getBranch().getId(),
                            CashSessionStatus.OPEN
                    )
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "An open cash session is required"
                            )
                    );
        }

        PurchasePayment payment = payments.save(
                new PurchasePayment(
                        purchase,
                        request.paymentMethod(),
                        request.paymentSource(),
                        cashSession,
                        request.amount(),
                        user,
                        terminal,
                        clock.instant()
                )
        );

        if (request.paymentSource()
                == PurchasePaymentSource.CASH_REGISTER) {

            movements.save(
                    new CashMovement(
                            cashSession,
                            CashMovementDirection.OUT,
                            CashMovementType.PURCHASE_PAYMENT,
                            payment.getAmount(),
                            "PURCHASE_PAYMENT",
                            payment.getId(),
                            "Pago de compra " + purchase.getId(),
                            user,
                            terminal
                    )
            );
        }

        log.info(
                "Purchase payment registered. purchaseId={}, paymentId={}, amount={}, method={}, source={}",
                purchaseId,
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentSource()
        );

        return PurchasePaymentResponse.from(payment);
    }
}