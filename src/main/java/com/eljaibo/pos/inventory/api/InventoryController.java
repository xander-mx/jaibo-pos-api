package com.eljaibo.pos.inventory.api;

import com.eljaibo.pos.inventory.application.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(
            InventoryService inventoryService
    ) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryResponse> findInventory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId
    ) {
        return inventoryService.findInventory(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId
        );
    }

    @PutMapping("/{productId}/quantity")
    public InventoryResponse adjustQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.adjustQuantity(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("user_id")
                        )
                ),
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId,
                productId,
                request
        );
    }

    @PostMapping("/{productId}/waste")
    public InventoryResponse registerWaste(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID productId,
            @Valid @RequestBody RegisterWasteRequest request
    ) {
        return inventoryService.registerWaste(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("user_id")
                        )
                ),
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId,
                productId,
                request
        );
    }
}