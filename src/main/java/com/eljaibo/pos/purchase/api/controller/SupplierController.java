package com.eljaibo.pos.purchase.api.controller;

import com.eljaibo.pos.purchase.api.request.CreateSupplierRequest;
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

        UUID businessId = UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString("business_id")
                )
        );

        return supplierService.create(
                businessId,
                request
        );
    }

    @GetMapping
    public List<SupplierResponse> findActive(
            @AuthenticationPrincipal Jwt jwt
    ) {

        UUID businessId = UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString("business_id")
                )
        );

        return supplierService.findActive(businessId);
    }
}