package com.eljaibo.pos.inventory.api;

import com.eljaibo.pos.catalog.domain.InventoryMode;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        String productName,
        InventoryMode inventoryMode,
        UUID unitId,
        String unitCode,
        String unitSymbol,
        BigDecimal quantity
) {
}