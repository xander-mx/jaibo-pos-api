package com.eljaibo.pos.catalog.api;

import com.eljaibo.pos.catalog.domain.InventoryMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProductRequest(
        @NotNull
        UUID categoryId,

        UUID inventoryUnitId,

        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 500)
        String imageUrl,

        @NotNull
        InventoryMode inventoryMode,

        @NotNull
        Boolean sellable,

        @NotNull
        Boolean purchasable,

        @PositiveOrZero
        Integer displayOrder
) {
}