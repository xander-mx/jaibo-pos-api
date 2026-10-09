package com.eljaibo.pos.catalog.api;

import com.eljaibo.pos.catalog.application.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/units")
    public List<UnitResponse> findUnits() {
        return catalogService.findActiveUnits();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return catalogService.createCategory(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                request
        );
    }

    @GetMapping("/categories")
    public List<CategoryResponse> findCategories(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return catalogService.findActiveCategories(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id")))
        );
    }
}