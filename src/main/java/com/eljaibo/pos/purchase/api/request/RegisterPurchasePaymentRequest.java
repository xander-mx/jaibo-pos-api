package com.eljaibo.pos.purchase.api.request;

import com.eljaibo.pos.purchase.domain.PurchasePaymentSource;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegisterPurchasePaymentRequest(

        @NotNull
        PaymentMethod paymentMethod,

        @NotNull
        PurchasePaymentSource paymentSource,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount

) {
}