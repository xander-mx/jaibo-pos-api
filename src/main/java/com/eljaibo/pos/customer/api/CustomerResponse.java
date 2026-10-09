package com.eljaibo.pos.customer.api;

import com.eljaibo.pos.customer.domain.Customer;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String email,
        boolean frequent,
        boolean wholesale,
        boolean creditEnabled,
        boolean active
) {

    public static CustomerResponse from(
            Customer customer
    ) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.isFrequent(),
                customer.isWholesale(),
                customer.isCreditEnabled(),
                customer.isActive()
        );
    }
}