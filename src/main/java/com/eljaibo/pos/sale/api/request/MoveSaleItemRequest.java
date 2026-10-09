package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveSaleItemRequest(
        @NotNull UUID targetSaleId
) {
}