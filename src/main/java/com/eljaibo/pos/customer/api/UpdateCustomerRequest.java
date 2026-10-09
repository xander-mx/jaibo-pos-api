package com.eljaibo.pos.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 30)
        String phone,

        @Email
        @Size(max = 254)
        String email,

        boolean frequent,
        boolean wholesale,
        boolean creditEnabled
) {
}