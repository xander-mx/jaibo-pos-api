package com.eljaibo.pos.customer.api;

import com.eljaibo.pos.customer.application.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        return customerService.create(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                request
        );
    }

    @GetMapping
    public List<CustomerResponse> findActive(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, name = "q")
            String search
    ) {
        return customerService.findActive(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                search
        );
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return customerService.update(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                customerId,
                request
        );
    }
}