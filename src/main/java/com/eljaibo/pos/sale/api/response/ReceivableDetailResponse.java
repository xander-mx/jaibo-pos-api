package com.eljaibo.pos.sale.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceivableDetailResponse(
        UUID id,
        UUID saleId,
        int saleNumber,
        Instant openedAt,

        BigDecimal originalAmount,
        BigDecimal balance,

        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal courtesyTotal,
        BigDecimal saleDiscountAmount,
        BigDecimal total,

        List<SaleItemResponse> items,
        List<Payment> payments
) {

    public record Payment(
            UUID id,
            BigDecimal amount,
            Instant receivedAt
    ) {
    }
}