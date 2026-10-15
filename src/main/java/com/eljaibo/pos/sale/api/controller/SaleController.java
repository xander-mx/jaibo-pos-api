package com.eljaibo.pos.sale.api.controller;

import com.eljaibo.pos.sale.api.request.AddSaleItemRequest;
import com.eljaibo.pos.sale.api.request.ApplySaleDiscountRequest;
import com.eljaibo.pos.sale.api.request.ApplySaleItemDiscountRequest;
import com.eljaibo.pos.sale.api.request.AssignSaleCustomerRequest;
import com.eljaibo.pos.sale.api.request.CancelSaleRequest;
import com.eljaibo.pos.sale.api.request.ChangeSaleItemPriceRequest;
import com.eljaibo.pos.sale.api.request.ChangeSaleItemQuantityRequest;
import com.eljaibo.pos.sale.api.request.MoveSaleItemRequest;
import com.eljaibo.pos.sale.api.request.OpenSaleRequest;
import com.eljaibo.pos.sale.api.request.PaySaleRequest;
import com.eljaibo.pos.sale.api.request.SplitSaleItemRequest;
import com.eljaibo.pos.sale.api.response.SaleDetailResponse;
import com.eljaibo.pos.sale.api.response.SaleResponse;
import com.eljaibo.pos.sale.application.CreditPaymentService;
import com.eljaibo.pos.sale.application.SaleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/sales")
public class SaleController {
    private final SaleService saleService;
    private final CreditPaymentService creditPaymentService;

    public SaleController(SaleService saleService, CreditPaymentService creditPaymentService) {
        this.saleService = saleService;
        this.creditPaymentService = creditPaymentService;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse open(@AuthenticationPrincipal Jwt jwt,
                             @RequestHeader("X-Terminal-Id") UUID terminalId,
                             @Valid @RequestBody OpenSaleRequest request) {
        return saleService.open(claim(jwt, "user_id"), claim(jwt, "business_id"), terminalId, request);
    }

    @GetMapping("/open")
    public List<SaleResponse> findOpen(@AuthenticationPrincipal Jwt jwt,
                                      @RequestHeader("X-Terminal-Id") UUID terminalId) {
        return saleService.findOpen(claim(jwt, "user_id"), claim(jwt, "business_id"), terminalId);
    }

    @PostMapping("/{saleId}/items")
    public SaleDetailResponse addItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @Valid @RequestBody AddSaleItemRequest request
    ) {
        return saleService.addItem(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    @GetMapping("/{saleId}")
    public SaleDetailResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId
    ) {
        return saleService.findById(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId
        );
    }

    @PatchMapping(
            "/{saleId}/items/{itemId}/quantity"
    )
    public SaleDetailResponse changeItemQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody
            ChangeSaleItemQuantityRequest request
    ) {
        return saleService.changeItemQuantity(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId,
                request
        );
    }

    @DeleteMapping(
            "/{saleId}/items/{itemId}"
    )
    public SaleDetailResponse removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId
    ) {
        return saleService.removeItem(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId
        );
    }

    @PatchMapping(
            "/{saleId}/items/{itemId}/price"
    )
    public SaleDetailResponse changeItemPrice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody
            ChangeSaleItemPriceRequest request
    ) {
        return saleService.changeItemPrice(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId,
                request
        );
    }

    @PatchMapping(
            "/{saleId}/items/{itemId}/discount"
    )
    public SaleDetailResponse applyItemDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody
            ApplySaleItemDiscountRequest request
    ) {
        return saleService.applyItemDiscount(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId,
                request
        );
    }

    @DeleteMapping(
            "/{saleId}/items/{itemId}/discount"
    )
    public SaleDetailResponse removeItemDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId
    ) {
        return saleService.removeItemDiscount(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId
        );
    }

    @PatchMapping(
            "/{saleId}/items/{itemId}/courtesy"
    )
    public SaleDetailResponse markItemCourtesy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId
    ) {
        return saleService.markItemCourtesy(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId
        );
    }

    @DeleteMapping(
            "/{saleId}/items/{itemId}/courtesy"
    )
    public SaleDetailResponse removeItemCourtesy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId
    ) {
        return saleService.removeItemCourtesy(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId
        );
    }

    @PatchMapping("/{saleId}/discount")
    public SaleDetailResponse applySaleDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @Valid @RequestBody
            ApplySaleDiscountRequest request
    ) {
        return saleService.applySaleDiscount(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    @DeleteMapping("/{saleId}/discount")
    public SaleDetailResponse removeSaleDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId
    ) {
        return saleService.removeSaleDiscount(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId
        );
    }

    @PostMapping("/{saleId}/pay")
    public SaleDetailResponse pay(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @Valid @RequestBody PaySaleRequest request
    ) {
        return saleService.pay(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    @PatchMapping("/{saleId}/customer")
    public SaleDetailResponse assignCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @Valid @RequestBody AssignSaleCustomerRequest request
    ) {
        return saleService.assignCustomer(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    @DeleteMapping("/{saleId}/customer")
    public SaleDetailResponse removeCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId
    ) {
        return saleService.removeCustomer(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId
        );
    }

    @PostMapping(
            "/{saleId}/items/{itemId}/move"
    )
    public SaleDetailResponse moveItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody
            MoveSaleItemRequest request
    ) {
        return saleService.moveItem(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId,
                request
        );
    }

    @PostMapping("/{saleId}/items/{itemId}/split")
    public SaleDetailResponse splitItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SplitSaleItemRequest request
    ) {
        return saleService.splitItem(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                itemId,
                request
        );
    }

    @PostMapping("/{saleId}/cancel")
    public SaleDetailResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID saleId,
            @Valid @RequestBody CancelSaleRequest request
    ) {
        return saleService.cancel(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                saleId,
                request
        );
    }

    private UUID claim(Jwt jwt, String name) {
        return UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString(name)));
    }
}
