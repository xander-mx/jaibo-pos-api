package com.eljaibo.pos.sale.api.controller;

import com.eljaibo.pos.sale.api.response.ReceivableDetailResponse;
import com.eljaibo.pos.sale.api.response.ReceivableResponse;
import com.eljaibo.pos.sale.application.ReceivableService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/customers/{customerId}/receivables"
)
public class ReceivableController {

    private final ReceivableService service;

    public ReceivableController(
            ReceivableService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<ReceivableResponse> findOpen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId
    ) {
        return service.findOpenByCustomer(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                customerId
        );
    }

    @GetMapping("/paid")
    public List<ReceivableResponse> findPaid(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId
    ) {
        return service.findPaidByCustomer(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                customerId
        );
    }

    @GetMapping("/{receivableId}")
    public ReceivableDetailResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId,
            @PathVariable UUID receivableId
    ) {
        return service.findById(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                customerId,
                receivableId
        );
    }
}