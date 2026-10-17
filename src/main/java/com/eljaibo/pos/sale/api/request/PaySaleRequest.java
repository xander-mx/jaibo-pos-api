package com.eljaibo.pos.sale.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PaySaleRequest(
        @NotNull
        List<@Valid PaymentRequest> payments
) {
}