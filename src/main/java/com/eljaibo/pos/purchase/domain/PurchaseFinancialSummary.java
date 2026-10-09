package com.eljaibo.pos.purchase.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PurchaseFinancialSummary(

        BigDecimal total,

        BigDecimal paid,

        BigDecimal balance,

        PurchasePaymentStatus status

) {

    public static PurchaseFinancialSummary calculate(
            BigDecimal total,
            BigDecimal paid
    ) {

        BigDecimal normalizedTotal = total.setScale(
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal normalizedPaid = paid.setScale(
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal balance = normalizedTotal.subtract(
                normalizedPaid
        );

        if (balance.signum() < 0) {
            throw new IllegalStateException(
                    "Purchase payments exceed total"
            );
        }

        PurchasePaymentStatus status;

        if (balance.signum() == 0) {

            status = PurchasePaymentStatus.PAID;

        } else if (normalizedPaid.signum() == 0) {

            status = PurchasePaymentStatus.PENDING;

        } else {

            status = PurchasePaymentStatus.PARTIALLY_PAID;
        }

        return new PurchaseFinancialSummary(
                normalizedTotal,
                normalizedPaid,
                balance,
                status
        );
    }
}