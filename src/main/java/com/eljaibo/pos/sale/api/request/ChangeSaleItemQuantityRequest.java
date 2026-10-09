package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ChangeSaleItemQuantityRequest(

        @NotNull
        @DecimalMin(value = "0.001")
        BigDecimal quantity

) {
}