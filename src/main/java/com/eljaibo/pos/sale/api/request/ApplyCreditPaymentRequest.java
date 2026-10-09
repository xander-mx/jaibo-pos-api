package com.eljaibo.pos.sale.api.request;

import com.eljaibo.pos.sale.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyCreditPaymentRequest(
        @NotNull UUID receivableId,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @Positive BigDecimal amount
) {
}