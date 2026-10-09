package com.eljaibo.pos.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPresentationResponse(
        UUID id,
        UUID productId,
        String name,
        UnitResponse unit,
        BigDecimal conversionFactor,
        BigDecimal salePrice,
        BigDecimal priceBasisQuantity,
        boolean variableQuantity,
        BigDecimal minimumQuantity,
        boolean openPrice,
        int displayOrder,
        boolean active
) {
}