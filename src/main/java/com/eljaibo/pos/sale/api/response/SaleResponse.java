package com.eljaibo.pos.sale.api.response;

import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SaleResponse(
        UUID id, UUID cashSessionId, UUID branchId, int saleNumber,
        String reference, UUID customerId, SaleStatus status,
        BigDecimal subtotal, BigDecimal lineDiscountTotal, BigDecimal courtesyTotal,
        DiscountType saleDiscountType, BigDecimal saleDiscountValue, BigDecimal saleDiscountAmount, BigDecimal total,
        UUID openedByUserId, UUID openedFromTerminalId, Instant createdAt, long version
) {
    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(), sale.getCashSession().getId(), sale.getBranch().getId(),
                sale.getSaleNumber(), sale.getReference(),
                sale.getCustomer() == null ? null : sale.getCustomer().getId(), sale.getStatus(),
                sale.getSubtotal(), sale.getLineDiscountTotal(), sale.getCourtesyTotal(),
                sale.getSaleDiscountType(),
                sale.getSaleDiscountValue(),
                sale.getSaleDiscountAmount(), sale.getTotal(), sale.getOpenedBy().getId(),
                sale.getOpenedFromTerminal().getId(), sale.getCreatedAt(), sale.getVersion());
    }
}
