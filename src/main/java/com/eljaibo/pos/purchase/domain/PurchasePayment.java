package com.eljaibo.pos.purchase.domain;

import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_payment")
public class PurchasePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 20
    )
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_source",
            nullable = false,
            length = 20
    )
    private PurchasePaymentSource paymentSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id")
    private CashSession cashSession;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private AppUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @Column(
            name = "paid_at",
            nullable = false,
            updatable = false
    )
    private Instant paidAt;

    protected PurchasePayment() {
        // Requerido por JPA.
    }

    public PurchasePayment(
            Purchase purchase,
            PaymentMethod paymentMethod,
            PurchasePaymentSource paymentSource,
            CashSession cashSession,
            BigDecimal amount,
            AppUser performedBy,
            Terminal terminal,
            Instant paidAt
    ) {

        if (purchase == null
                || purchase.getStatus() != PurchaseStatus.RECEIVED) {

            throw new IllegalArgumentException(
                    "A received purchase is required"
            );
        }

        if (paymentMethod == null
                || paymentSource == null) {

            throw new IllegalArgumentException(
                    "Payment method and source are required"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Payment amount supports at most two decimals"
            );
        }

        if (performedBy == null
                || terminal == null
                || paidAt == null) {

            throw new IllegalArgumentException(
                    "User, terminal and payment date are required"
            );
        }

        UUID businessId = purchase
                .getBranch()
                .getBusiness()
                .getId();

        if (!businessId.equals(
                performedBy.getBusiness().getId()
        )) {
            throw new IllegalArgumentException(
                    "User belongs to another business"
            );
        }

        if (!purchase.getBranch().getId()
                .equals(terminal.getBranch().getId())) {

            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        if (paymentSource
                == PurchasePaymentSource.CASH_REGISTER) {

            if (paymentMethod != PaymentMethod.CASH) {
                throw new IllegalArgumentException(
                        "Cash register payments must use cash"
                );
            }

            if (cashSession == null
                    || cashSession.getStatus()
                    != CashSessionStatus.OPEN) {

                throw new IllegalArgumentException(
                        "An open cash session is required"
                );
            }

            if (!cashSession
                    .getOperatingCycle()
                    .getBranch()
                    .getId()
                    .equals(purchase.getBranch().getId())) {

                throw new IllegalArgumentException(
                        "Cash session belongs to another branch"
                );
            }

        } else if (cashSession != null) {

            throw new IllegalArgumentException(
                    "External payments cannot reference a cash session"
            );
        }

        this.purchase = purchase;
        this.paymentMethod = paymentMethod;
        this.paymentSource = paymentSource;
        this.cashSession = cashSession;

        this.amount = amount.setScale(
                2,
                RoundingMode.HALF_UP
        );

        this.performedBy = performedBy;
        this.terminal = terminal;
        this.paidAt = paidAt;
    }

    public UUID getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PurchasePaymentSource getPaymentSource() {
        return paymentSource;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AppUser getPerformedBy() {
        return performedBy;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}