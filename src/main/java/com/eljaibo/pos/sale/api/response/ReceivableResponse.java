package com.eljaibo.pos.sale.api.response;

import com.eljaibo.pos.sale.domain.Receivable;
import com.eljaibo.pos.sale.domain.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceivableResponse(
        UUID id,
        UUID saleId,
        int saleNumber,
        BigDecimal originalAmount,
        BigDecimal balance,
        ReceivableStatus status,
        Instant openedAt,
        Instant paidAt
) {

    public static ReceivableResponse from(
            Receivable receivable
    ) {
        return new ReceivableResponse(
                receivable.getId(),
                receivable.getSale().getId(),
                receivable.getSale().getSaleNumber(),
                receivable.getOriginalAmount(),
                receivable.getBalance(),
                receivable.getStatus(),
                receivable.getOpenedAt(),
                receivable.getPaidAt()
        );
    }
}