package com.eljaibo.pos.sale.api.request;

import jakarta.validation.Valid;

import java.util.List;

public record CloseCreditSaleRequest(
        List<@Valid PaymentRequest> payments
) {
}
