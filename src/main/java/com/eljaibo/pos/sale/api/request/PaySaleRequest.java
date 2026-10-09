package com.eljaibo.pos.sale.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PaySaleRequest(
        @NotEmpty
        List<@Valid PaymentRequest> payments
) {
}