package com.eljaibo.pos.purchase.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 30)
        String phone

) {
}