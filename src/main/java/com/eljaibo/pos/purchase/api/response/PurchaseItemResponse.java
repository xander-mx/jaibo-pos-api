package com.eljaibo.pos.purchase.api.response;

import com.eljaibo.pos.purchase.domain.PurchaseItem;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal subtotal
) {

    public static PurchaseItemResponse from(
            PurchaseItem item
    ) {
        return new PurchaseItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitCost(),
                item.getSubtotal()
        );
    }
}