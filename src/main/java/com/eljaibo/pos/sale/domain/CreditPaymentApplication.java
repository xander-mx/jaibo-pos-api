package com.eljaibo.pos.sale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(
        name = "credit_payment_application",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_credit_payment_application",
                        columnNames = {
                                "credit_payment_id",
                                "receivable_id"
                        }
                )
        }
)
public class CreditPaymentApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "credit_payment_id",
            nullable = false
    )
    private CreditPayment creditPayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receivable_id",
            nullable = false
    )
    private Receivable receivable;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal amount;

    protected CreditPaymentApplication() {
        // Requerido por JPA.
    }

    public CreditPaymentApplication(
            CreditPayment creditPayment,
            Receivable receivable,
            BigDecimal amount
    ) {
        if (creditPayment == null) {
            throw new IllegalArgumentException(
                    "Credit payment is required"
            );
        }

        if (receivable == null) {
            throw new IllegalArgumentException(
                    "Receivable is required"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Application amount must be positive"
            );
        }

        if (!creditPayment
                .getCustomer()
                .getId()
                .equals(receivable.getCustomer().getId())) {

            throw new IllegalArgumentException(
                    "Payment and receivable belong to different customers"
            );
        }

        this.creditPayment = creditPayment;
        this.receivable = receivable;

        this.amount = amount.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    public UUID getId() {
        return id;
    }

    public CreditPayment getCreditPayment() {
        return creditPayment;
    }

    public Receivable getReceivable() {
        return receivable;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}