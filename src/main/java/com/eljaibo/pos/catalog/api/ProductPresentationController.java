package com.eljaibo.pos.catalog.api;

import com.eljaibo.pos.catalog.application.ProductPresentationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/products/{productId}/presentations"
)
public class ProductPresentationController {

    private final ProductPresentationService presentationService;

    public ProductPresentationController(
            ProductPresentationService presentationService
    ) {
        this.presentationService = presentationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductPresentationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody
            CreateProductPresentationRequest request
    ) {
        return presentationService.create(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                productId,
                request
        );
    }

    @GetMapping
    public List<ProductPresentationResponse> findActive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId
    ) {
        return presentationService.findActive(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                productId
        );
    }
}