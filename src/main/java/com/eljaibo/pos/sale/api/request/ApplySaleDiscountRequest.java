package com.eljaibo.pos.sale.api.request;

import com.eljaibo.pos.sale.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ApplySaleDiscountRequest(

        @NotNull
        DiscountType discountType,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal discountValue

) {
}