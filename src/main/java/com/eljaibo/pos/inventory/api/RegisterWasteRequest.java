package com.eljaibo.pos.inventory.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterWasteRequest(
        @NotNull
        @DecimalMin(value = "0.001")
        BigDecimal quantity,

        @Size(max = 255)
        String reason
) {
}