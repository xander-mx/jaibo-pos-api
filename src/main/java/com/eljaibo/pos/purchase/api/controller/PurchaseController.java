package com.eljaibo.pos.purchase.api.controller;

import com.eljaibo.pos.purchase.api.request.AddPurchaseItemRequest;
import com.eljaibo.pos.purchase.api.request.CreatePurchaseRequest;
import com.eljaibo.pos.purchase.api.request.RegisterPurchasePaymentRequest;
import com.eljaibo.pos.purchase.api.response.PurchaseDetailResponse;
import com.eljaibo.pos.purchase.api.response.PurchasePaymentResponse;
import com.eljaibo.pos.purchase.api.response.PurchaseSummaryResponse;
import com.eljaibo.pos.purchase.application.PurchasePaymentService;
import com.eljaibo.pos.purchase.application.PurchaseService;
import com.eljaibo.pos.purchase.domain.PurchasePaymentStatus;
import com.eljaibo.pos.purchase.domain.PurchaseStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchasePaymentService purchasePaymentService;

    public PurchaseController(
            PurchaseService purchaseService,
            PurchasePaymentService purchasePaymentService
    ) {
        this.purchaseService = purchaseService;
        this.purchasePaymentService = purchasePaymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDetailResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @Valid
            @RequestBody
            CreatePurchaseRequest request
    ) {
        return purchaseService.create(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "user_id"
                                )
                        )
                ),
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                terminalId,
                request
        );
    }

    @PostMapping("/{purchaseId}/items")
    public PurchaseDetailResponse addItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID purchaseId,
            @Valid @RequestBody AddPurchaseItemRequest request
    ) {
        return purchaseService.addItem(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                terminalId,
                purchaseId,
                request
        );
    }

    @PostMapping("/{purchaseId}/receive")
    public PurchaseDetailResponse receive(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID purchaseId
    ) {
        return purchaseService.receive(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "user_id"
                                )
                        )
                ),
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                terminalId,
                purchaseId
        );
    }

    @DeleteMapping("/{purchaseId}/items/{itemId}")
    public PurchaseDetailResponse removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID purchaseId,
            @PathVariable UUID itemId
    ) {
        return purchaseService.removeItem(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString(
                                        "business_id"
                                )
                        )
                ),
                terminalId,
                purchaseId,
                itemId
        );
    }

    @PostMapping("/{purchaseId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchasePaymentResponse registerPayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID purchaseId,
            @Valid @RequestBody RegisterPurchasePaymentRequest request
    ) {
        return purchasePaymentService.register(
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
                purchaseId,
                request
        );
    }

    @GetMapping("/{purchaseId}")
    public PurchaseDetailResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID purchaseId
    ) {

        return purchaseService.findById(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId,
                purchaseId
        );
    }

    @GetMapping
    public List<PurchaseSummaryResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @RequestParam(required = false)
            PurchaseStatus status,
            @RequestParam(required = false)
            PurchasePaymentStatus paymentStatus,
            @RequestParam(required = false)
            UUID supplierId,
            @RequestParam(defaultValue = "false")
            boolean pendingOnly
    ) {

        return purchaseService.findAll(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId,
                status,
                paymentStatus,
                supplierId,
                pendingOnly
        );
    }
}