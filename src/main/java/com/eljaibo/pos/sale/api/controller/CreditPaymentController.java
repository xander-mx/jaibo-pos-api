package com.eljaibo.pos.sale.api.controller;

import com.eljaibo.pos.sale.api.request.ApplyCreditPaymentRequest;
import com.eljaibo.pos.sale.api.request.AutomaticCreditPaymentRequest;
import com.eljaibo.pos.sale.api.response.CreditPaymentResponse;
import com.eljaibo.pos.sale.application.CreditPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/credit-payments")
public class CreditPaymentController {

    private final CreditPaymentService service;

    public CreditPaymentController(
            CreditPaymentService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apply(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID customerId,
            @Valid
            @RequestBody
            ApplyCreditPaymentRequest request
    ) {
        service.applyToReceivable(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                customerId,
                request
        );
    }

    @PostMapping("/automatic")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyAutomatically(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID customerId,
            @Valid
            @RequestBody
            AutomaticCreditPaymentRequest request
    ) {
        service.applyAutomatically(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                customerId,
                request
        );
    }


    @GetMapping
    public List<CreditPaymentResponse> findCreditPayments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId
    ) {
        return service.findByCustomer(
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