package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddSaleItemRequest(

        @NotNull
        UUID presentationId,

        @NotNull
        @DecimalMin(value = "0.001")
        BigDecimal quantity,

        @DecimalMin(value = "0.00")
        BigDecimal unitPrice

) {
}