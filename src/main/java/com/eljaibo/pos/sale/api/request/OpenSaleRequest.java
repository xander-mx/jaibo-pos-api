package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OpenSaleRequest(
        @NotNull UUID cashSessionId,
        @Size(max = 80) String reference,
        UUID customerId
) {
}
