package com.eljaibo.pos.sale.domain;

import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_payment")
public class CreditPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 20
    )
    private PaymentMethod paymentMethod;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by_user_id", nullable = false)
    private AppUser receivedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "received_from_terminal_id",
            nullable = false
    )
    private Terminal receivedFromTerminal;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected CreditPayment() {
        // Requerido por JPA.
    }

    public CreditPayment(
            Customer customer,
            CashSession cashSession,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            AppUser receivedBy,
            Terminal receivedFromTerminal,
            Instant receivedAt
    ) {
        if (customer == null || !customer.isActive()) {
            throw new IllegalArgumentException(
                    "An active customer is required"
            );
        }

        if (cashSession == null
                || cashSession.getStatus() != CashSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                    "An open cash session is required"
            );
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        if (receivedBy == null
                || receivedFromTerminal == null
                || receivedAt == null) {
            throw new IllegalArgumentException(
                    "User, terminal and received time are required"
            );
        }

        UUID businessId = cashSession
                .getOperatingCycle()
                .getBranch()
                .getBusiness()
                .getId();

        if (!customer.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException(
                    "Customer belongs to another business"
            );
        }

        if (!receivedBy.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException(
                    "User belongs to another business"
            );
        }

        UUID branchId = cashSession
                .getOperatingCycle()
                .getBranch()
                .getId();

        if (!receivedFromTerminal
                .getBranch()
                .getId()
                .equals(branchId)) {

            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        this.customer = customer;
        this.cashSession = cashSession;
        this.paymentMethod = paymentMethod;

        this.amount = amount.setScale(
                2,
                RoundingMode.HALF_UP
        );

        this.receivedBy = receivedBy;
        this.receivedFromTerminal = receivedFromTerminal;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AppUser getReceivedBy() {
        return receivedBy;
    }

    public Terminal getReceivedFromTerminal() {
        return receivedFromTerminal;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}