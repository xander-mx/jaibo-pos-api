package com.eljaibo.pos.sale.api.response;

import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.Sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleDetailResponse(

        UUID id,
        UUID cashSessionId,
        UUID branchId,

        int saleNumber,
        String reference,
        UUID customerId,

        String status,

        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal courtesyTotal,
        BigDecimal saleDiscountAmount,
        BigDecimal total,

        List<SaleItemResponse> items,

        UUID openedByUserId,
        UUID openedFromTerminalId,

        DiscountType saleDiscountType,
        BigDecimal saleDiscountValue,

        Instant createdAt,
        long version

) {

    public static SaleDetailResponse from(
            Sale sale,
            List<SaleItemResponse> items
    ) {
        return new SaleDetailResponse(
                sale.getId(),
                sale.getCashSession().getId(),
                sale.getBranch().getId(),

                sale.getSaleNumber(),
                sale.getReference(),

                sale.getCustomer() == null
                        ? null
                        : sale.getCustomer().getId(),

                sale.getStatus().name(),

                sale.getSubtotal(),
                sale.getLineDiscountTotal(),
                sale.getCourtesyTotal(),
                sale.getSaleDiscountAmount(),
                sale.getTotal(),

                items,

                sale.getOpenedBy().getId(),
                sale.getOpenedFromTerminal().getId(),

                sale.getSaleDiscountType(),
                sale.getSaleDiscountValue(),

                sale.getCreatedAt(),
                sale.getVersion()
        );
    }
}