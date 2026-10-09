package com.eljaibo.pos.sale.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelSaleRequest(
        @NotBlank
        @Size(max = 200)
        String reason
) {
}