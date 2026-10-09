package com.eljaibo.pos.purchase.api.response;

import com.eljaibo.pos.purchase.domain.Supplier;

import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String phone,
        boolean active
) {

    public static SupplierResponse from(
            Supplier supplier
    ) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.isActive()
        );
    }
}