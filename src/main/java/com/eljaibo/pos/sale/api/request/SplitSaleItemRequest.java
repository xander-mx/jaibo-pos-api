package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitSaleItemRequest(
        @NotNull UUID targetSaleId,

        @NotNull
        @DecimalMin(value = "0.001")
        BigDecimal quantity
) {
}