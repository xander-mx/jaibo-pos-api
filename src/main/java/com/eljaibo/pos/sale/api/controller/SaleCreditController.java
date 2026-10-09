package com.eljaibo.pos.sale.api.controller;

import com.eljaibo.pos.sale.api.request.CloseCreditSaleRequest;
import com.eljaibo.pos.sale.api.response.SaleDetailResponse;
import com.eljaibo.pos.sale.application.SaleCreditService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleCreditController {

    private final SaleCreditService saleCreditService;

    public SaleCreditController(
            SaleCreditService saleCreditService
    ) {
        this.saleCreditService = saleCreditService;
    }

    @PostMapping("/{saleId}/credit")
    public SaleDetailResponse closeAsCredit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @Valid
            @RequestBody(required = false)
            CloseCreditSaleRequest request
    ) {
        return saleCreditService.closeAsCredit(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    private UUID claim(
            Jwt jwt,
            String name
    ) {
        return UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString(name)
                )
        );
    }
}