package com.eljaibo.pos.sale.api.request;

import com.eljaibo.pos.sale.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AutomaticCreditPaymentRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotNull @Positive BigDecimal amount
) {
}
