package com.eljaibo.pos.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdjustInventoryRequest(
        @NotNull
        BigDecimal quantity,

        @Size(max = 255)
        String reason
) {
}