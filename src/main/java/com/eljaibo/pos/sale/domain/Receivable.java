package com.eljaibo.pos.sale.domain;

import com.eljaibo.pos.customer.domain.Customer;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "receivable",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_receivable_sale",
                        columnNames = "sale_id"
                )
        }
)
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(
            name = "original_amount",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal originalAmount;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivableStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Receivable() {
        // Requerido por JPA.
    }

    public Receivable(
            Sale sale,
            BigDecimal initialBalance,
            Instant openedAt
    ) {
        if (sale == null) {
            throw new IllegalArgumentException(
                    "Sale is required"
            );
        }

        if (sale.getStatus() != SaleStatus.CREDIT) {
            throw new IllegalArgumentException(
                    "A credit sale is required"
            );
        }

        if (sale.getCustomer() == null) {
            throw new IllegalArgumentException(
                    "A customer is required"
            );
        }

        if (initialBalance == null) {
            throw new IllegalArgumentException(
                    "Initial balance is required"
            );
        }

        if (openedAt == null) {
            throw new IllegalArgumentException(
                    "Opened time is required"
            );
        }

        BigDecimal normalizedTotal =
                sale.getTotal()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal normalizedBalance =
                initialBalance
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (normalizedTotal.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Receivable amount must be positive"
            );
        }

        if (normalizedBalance.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Receivable balance must be positive"
            );
        }

        if (normalizedBalance.compareTo(normalizedTotal) > 0) {
            throw new IllegalArgumentException(
                    "Receivable balance cannot exceed sale total"
            );
        }

        this.sale = sale;
        this.customer = sale.getCustomer();

        this.originalAmount = normalizedTotal;
        this.balance = normalizedBalance;

        this.status = ReceivableStatus.OPEN;
        this.openedAt = openedAt;
    }

    public void applyPayment(
            BigDecimal amount,
            Instant now
    ) {
        if (status != ReceivableStatus.OPEN) {
            throw new IllegalStateException(
                    "Only an open receivable can receive payments"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "Payment time is required"
            );
        }

        BigDecimal normalizedAmount =
                amount.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        if (normalizedAmount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                    "Payment cannot exceed receivable balance"
            );
        }

        balance = balance
                .subtract(normalizedAmount)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        if (balance.signum() == 0) {
            status = ReceivableStatus.PAID;
            paidAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public ReceivableStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public long getVersion() {
        return version;
    }
}