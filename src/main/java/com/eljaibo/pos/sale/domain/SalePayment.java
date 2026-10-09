package com.eljaibo.pos.sale.domain;

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
@Table(name = "sale_payment")
public class SalePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

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
    @JoinColumn(name = "received_from_terminal_id", nullable = false)
    private Terminal receivedFromTerminal;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected SalePayment() {
        // Requerido por JPA.
    }

    public SalePayment(
            Sale sale,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            AppUser receivedBy,
            Terminal receivedFromTerminal,
            Instant receivedAt
    ) {
        if (sale == null) {
            throw new IllegalArgumentException(
                    "Sale is required"
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

        if (!receivedFromTerminal.getBranch().getId()
                .equals(sale.getBranch().getId())) {
            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        this.sale = sale;
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

    public Sale getSale() {
        return sale;
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
