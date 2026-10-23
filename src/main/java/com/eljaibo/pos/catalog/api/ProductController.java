package com.eljaibo.pos.catalog.api;

import com.eljaibo.pos.catalog.application.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest request
    ) {
        return productService.create(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                request
        );
    }

    @GetMapping
    public List<ProductResponse> findActive(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false")
            boolean sellableOnly,
            @RequestParam(defaultValue = "false")
            boolean purchasableOnly
    ) {
        return productService.findActive(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                sellableOnly,
                purchasableOnly
        );
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.update(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                productId,
                request
        );
    }
}