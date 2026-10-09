package com.eljaibo.pos.catalog.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductPresentationRequest(
        @NotNull
        UUID unitId,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @DecimalMin("0.000001")
        @Digits(integer = 8, fraction = 6)
        BigDecimal conversionFactor,

        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal salePrice,

        @NotNull
        @DecimalMin("0.001")
        @Digits(integer = 11, fraction = 3)
        BigDecimal priceBasisQuantity,

        @NotNull
        Boolean variableQuantity,

        @DecimalMin("0.001")
        @Digits(integer = 11, fraction = 3)
        BigDecimal minimumQuantity,

        @NotNull
        Boolean openPrice,

        @PositiveOrZero
        Integer displayOrder
) {
}