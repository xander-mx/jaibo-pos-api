package com.eljaibo.pos.purchase.api.controller;

import com.eljaibo.pos.purchase.api.request.CreateSupplierRequest;
import com.eljaibo.pos.purchase.api.request.UpdateSupplierRequest;
import com.eljaibo.pos.purchase.api.response.SupplierResponse;
import com.eljaibo.pos.purchase.application.SupplierService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(
            SupplierService supplierService
    ) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSupplierRequest request
    ) {

        return supplierService.create(
                businessId(jwt),
                request
        );
    }

    @GetMapping
    public List<SupplierResponse> findAll(
            @AuthenticationPrincipal Jwt jwt
    ) {

        UUID businessId = businessId(jwt);

        return supplierService.findAll(
                businessId
        );
    }

    @PutMapping("/{supplierId}")
    public SupplierResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {

        return supplierService.update(
                businessId(jwt),
                supplierId,
                request
        );
    }

    @PatchMapping("/{supplierId}/deactivate")
    public SupplierResponse deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID supplierId
    ) {

        return supplierService.deactivate(
                businessId(jwt),
                supplierId
        );
    }

    @PatchMapping("/{supplierId}/activate")
    public SupplierResponse activate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID supplierId
    ) {

        return supplierService.activate(
                businessId(jwt),
                supplierId
        );
    }

    private UUID businessId(Jwt jwt) {
        return UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString("business_id")
                )
        );
    }
}