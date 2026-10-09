package com.eljaibo.pos.catalog.api;

import com.eljaibo.pos.catalog.domain.InventoryMode;

import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        InventoryMode inventoryMode,
        boolean sellable,
        boolean purchasable,
        int displayOrder,
        boolean active,
        Category category,
        UnitResponse inventoryUnit
) {

    public record Category(
            UUID id,
            String name
    ) {
    }
}