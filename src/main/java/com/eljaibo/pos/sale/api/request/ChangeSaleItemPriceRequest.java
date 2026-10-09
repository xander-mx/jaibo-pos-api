package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ChangeSaleItemPriceRequest(

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal unitPrice

) {
}