package com.eljaibo.pos.purchase.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddPurchaseItemRequest(
        @NotNull
        UUID productId,

        @NotNull
        @DecimalMin(value = "0.001")
        BigDecimal quantity,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal unitCost
) {
}