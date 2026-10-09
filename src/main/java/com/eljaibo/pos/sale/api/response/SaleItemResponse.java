package com.eljaibo.pos.sale.api.response;

import com.eljaibo.pos.sale.domain.DiscountType;
import com.eljaibo.pos.sale.domain.SaleItem;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(

        UUID id,
        UUID productId,
        UUID presentationId,

        String productName,
        String presentationName,
        String unitCode,
        String unitSymbol,

        BigDecimal quantity,
        BigDecimal conversionFactor,

        BigDecimal unitPrice,
        BigDecimal priceBasisQuantity,

        BigDecimal grossAmount,

        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,

        boolean courtesy,
        BigDecimal lineTotal,

        UUID createdByUserId,
        UUID createdFromTerminalId,

        long version

) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getPresentation().getId(),

                item.getProductName(),
                item.getPresentationName(),
                item.getUnitCode(),
                item.getUnitSymbol(),

                item.getQuantity(),
                item.getConversionFactor(),

                item.getUnitPrice(),
                item.getPriceBasisQuantity(),

                item.getGrossAmount(),

                item.getDiscountType(),
                item.getDiscountValue(),
                item.getDiscountAmount(),

                item.isCourtesy(),
                item.getLineTotal(),

                item.getCreatedBy().getId(),
                item.getCreatedFromTerminal().getId(),

                item.getVersion()
        );
    }
}