package com.eljaibo.pos.sale.application;

import com.eljaibo.pos.cash.domain.CashMovement;
import com.eljaibo.pos.cash.domain.CashMovementDirection;
import com.eljaibo.pos.cash.domain.CashMovementType;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.cash.persistence.CashMovementRepository;
import com.eljaibo.pos.cash.persistence.CashSessionRepository;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.customer.persistence.CustomerRepository;
import com.eljaibo.pos.sale.api.request.ApplyCreditPaymentRequest;
import com.eljaibo.pos.sale.api.request.AutomaticCreditPaymentRequest;
import com.eljaibo.pos.sale.api.response.CreditPaymentResponse;
import com.eljaibo.pos.sale.domain.CreditPayment;
import com.eljaibo.pos.sale.domain.CreditPaymentApplication;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.sale.domain.Receivable;
import com.eljaibo.pos.sale.domain.ReceivableStatus;
import com.eljaibo.pos.sale.persistence.CreditPaymentApplicationRepository;
import com.eljaibo.pos.sale.persistence.CreditPaymentRepository;
import com.eljaibo.pos.sale.persistence.ReceivableRepository;
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
public class CreditPaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(CreditPaymentService.class);

    private final CreditPaymentRepository creditPayments;
    private final CreditPaymentApplicationRepository applications;
    private final ReceivableRepository receivables;
    private final CustomerRepository customers;
    private final CashSessionRepository sessions;
    private final AppUserRepository users;
    private final TerminalRepository terminals;
    private final CashMovementRepository cashMovements;
    private final Clock clock;

    public CreditPaymentService(
            CreditPaymentRepository creditPayments,
            CreditPaymentApplicationRepository applications,
            ReceivableRepository receivables,
            CustomerRepository customers,
            CashSessionRepository sessions,
            AppUserRepository users,
            TerminalRepository terminals,
            CashMovementRepository cashMovements,
            Clock clock
    ) {
        this.creditPayments = creditPayments;
        this.applications = applications;
        this.receivables = receivables;
        this.customers = customers;
        this.sessions = sessions;
        this.users = users;
        this.terminals = terminals;
        this.cashMovements = cashMovements;
        this.clock = clock;
    }

    @Transactional
    public void applyToReceivable(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID customerId,
            ApplyCreditPaymentRequest request
    ) {
        log.info(
                "Starting credit payment. customerId={}, receivableId={}, amount={}, method={}",
                customerId,
                request.receivableId(),
                request.amount(),
                request.paymentMethod()
        );

        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        CashSession session = sessions
                .findByOperatingCycleBranchIdAndStatus(
                        terminal.getBranch().getId(),
                        CashSessionStatus.OPEN
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "An open cash session is required"
                        )
                );

        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open cash session is required"
            );
        }

        if (!session.getOperatingCycle()
                .getBranch()
                .getId()
                .equals(terminal.getBranch().getId())) {

            throw notFound("Cash session not found");
        }

        Customer customer = customers
                .findById(customerId)
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

        Receivable receivable = receivables
                .findById(request.receivableId())
                .orElseThrow(
                        () -> notFound(
                                "Receivable not found"
                        )
                );

        if (!receivable.getCustomer()
                .getId()
                .equals(customerId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Receivable belongs to another customer"
            );
        }

        if (receivable.getStatus()
                != ReceivableStatus.OPEN) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an open receivable can receive payments"
            );
        }

        if (request.amount()
                .compareTo(receivable.getBalance()) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot exceed receivable balance"
            );
        }

        Instant now = clock.instant();

        CreditPayment payment =
                new CreditPayment(
                        customer,
                        session,
                        request.paymentMethod(),
                        request.amount(),
                        user,
                        terminal,
                        now
                );

        creditPayments.save(payment);

        if (request.paymentMethod() == PaymentMethod.CASH) {
            cashMovements.save(
                    new CashMovement(
                            session,
                            CashMovementDirection.IN,
                            CashMovementType.CUSTOMER_PAYMENT,
                            request.amount(),
                            "CREDIT_PAYMENT",
                            payment.getId(),
                            "Abono de cliente en efectivo",
                            user,
                            terminal
                    )
            );
        }

        receivable.applyPayment(
                request.amount(),
                now
        );

        CreditPaymentApplication application =
                new CreditPaymentApplication(
                        payment,
                        receivable,
                        request.amount()
                );

        applications.save(application);

        log.info(
                "Credit payment applied successfully. paymentId={}, receivableId={}, appliedAmount={}, remainingBalance={}",
                payment.getId(),
                receivable.getId(),
                request.amount(),
                receivable.getBalance()
        );
    }

    @Transactional
    public void applyAutomatically(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID customerId,
            AutomaticCreditPaymentRequest request
    ) {
        log.info(
                "Starting automatic credit payment. customerId={}, amount={}, method={}",
                customerId,
                request.amount(),
                request.paymentMethod()
        );

        AppUser user = findUser(
                userId,
                businessId
        );

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        CashSession session = sessions
                .findByOperatingCycleBranchIdAndStatus(
                        terminal.getBranch().getId(),
                        CashSessionStatus.OPEN
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "An open cash session is required"
                        )
                );

        Customer customer = customers
                .findById(customerId)
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

        List<Receivable> openReceivables =
                receivables
                        .findAllByCustomerIdAndStatusOrderByOpenedAtAscIdAsc(
                                customerId,
                                ReceivableStatus.OPEN
                        );

        if (openReceivables.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Customer has no open receivables"
            );
        }

        BigDecimal paymentAmount =
                request.amount()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal totalBalance =
                openReceivables
                        .stream()
                        .map(Receivable::getBalance)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (paymentAmount.compareTo(totalBalance) > 0) {
            log.warn(
                    "Automatic credit payment rejected because amount exceeds customer debt. customerId={}, amount={}, totalBalance={}",
                    customerId,
                    paymentAmount,
                    totalBalance
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot exceed customer outstanding balance"
            );
        }

        Instant now = clock.instant();

        CreditPayment payment =
                new CreditPayment(
                        customer,
                        session,
                        request.paymentMethod(),
                        paymentAmount,
                        user,
                        terminal,
                        now
                );

        creditPayments.save(payment);

        if (request.paymentMethod() == PaymentMethod.CASH) {
            cashMovements.save(
                    new CashMovement(
                            session,
                            CashMovementDirection.IN,
                            CashMovementType.CUSTOMER_PAYMENT,
                            request.amount(),
                            "CREDIT_PAYMENT",
                            payment.getId(),
                            "Abono de cliente en efectivo",
                            user,
                            terminal
                    )
            );
        }

        BigDecimal remaining = paymentAmount;

        for (Receivable receivable : openReceivables) {

            if (remaining.signum() == 0) {
                break;
            }

            BigDecimal applied =
                    remaining.min(
                            receivable.getBalance()
                    );

            receivable.applyPayment(
                    applied,
                    now
            );

            applications.save(
                    new CreditPaymentApplication(
                            payment,
                            receivable,
                            applied
                    )
            );

            remaining =
                    remaining
                            .subtract(applied)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            log.debug(
                    "Automatic payment application. paymentId={}, receivableId={}, applied={}, remainingPayment={}, receivableBalance={}",
                    payment.getId(),
                    receivable.getId(),
                    applied,
                    remaining,
                    receivable.getBalance()
            );
        }

        if (remaining.signum() != 0) {
            throw new IllegalStateException(
                    "Automatic payment was not fully applied"
            );
        }

        log.info(
                "Automatic credit payment applied successfully. paymentId={}, customerId={}, amount={}, previousBalance={}, remainingCustomerBalance={}",
                payment.getId(),
                customerId,
                paymentAmount,
                totalBalance,
                totalBalance.subtract(paymentAmount)
        );
    }

    @Transactional(readOnly = true)
    public List<CreditPaymentResponse> findByCustomer(
            UUID businessId,
            UUID customerId
    ) {
        customers
                .findByIdAndBusinessId(
                        customerId,
                        businessId
                )
                .filter(Customer::isActive)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active customer not found"
                        )
                );

        return creditPayments
                .findAllByCustomerIdOrderByReceivedAtAsc(
                        customerId
                )
                .stream()
                .map(CreditPaymentResponse::from)
                .toList();
    }

    private AppUser findUser(
            UUID userId,
            UUID businessId
    ) {
        return users
                .findById(userId)
                .filter(user ->
                        user.isActive()
                                && user.getBusiness()
                                .getId()
                                .equals(businessId)
                                && user.getBusiness()
                                .isActive()
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Active user required"
                        )
                );
    }

    private Terminal findTerminal(
            UUID terminalId,
            UUID businessId
    ) {
        return terminals
                .findByIdAndBranchBusinessIdAndActiveTrue(
                        terminalId,
                        businessId
                )
                .filter(terminal ->
                        terminal.getBranch().isActive()
                )
                .orElseThrow(
                        () -> notFound(
                                "Active terminal not found"
                        )
                );
    }

    private ResponseStatusException notFound(
            String reason
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                reason
        );
    }
}