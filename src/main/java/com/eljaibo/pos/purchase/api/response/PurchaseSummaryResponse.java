package com.eljaibo.pos.purchase.api.response;

import com.eljaibo.pos.purchase.domain.Purchase;
import com.eljaibo.pos.purchase.domain.PurchaseFinancialSummary;
import com.eljaibo.pos.purchase.domain.PurchasePaymentStatus;
import com.eljaibo.pos.purchase.domain.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseSummaryResponse(

        UUID id,

        PurchaseStatus status,

        UUID supplierId,

        String supplierName,

        BigDecimal total,

        BigDecimal paidAmount,

        BigDecimal pendingBalance,

        PurchasePaymentStatus paymentStatus,

        String note,

        Instant createdAt,

        Instant receivedAt

) {

    public static PurchaseSummaryResponse from(
            Purchase purchase,
            PurchaseFinancialSummary financial
    ) {

        return new PurchaseSummaryResponse(
                purchase.getId(),

                purchase.getStatus(),

                purchase.getSupplier() != null
                        ? purchase.getSupplier().getId()
                        : null,

                purchase.getSupplier() != null
                        ? purchase.getSupplier().getName()
                        : null,

                purchase.getTotal(),

                financial.paid(),

                financial.balance(),

                purchase.getStatus() == PurchaseStatus.DRAFT
                        ? null
                        : financial.status(),

                purchase.getNote(),

                purchase.getCreatedAt(),

                purchase.getReceivedAt()
        );
    }
}