package com.eljaibo.pos.purchase.api.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePurchaseRequest(

        UUID supplierId,

        @Size(max = 255)
        String note

) {
}