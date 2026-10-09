package com.eljaibo.pos.purchase.application;

import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.persistence.ProductRepository;
import com.eljaibo.pos.inventory.application.InventoryService;
import com.eljaibo.pos.purchase.api.request.AddPurchaseItemRequest;
import com.eljaibo.pos.purchase.api.request.CreatePurchaseRequest;
import com.eljaibo.pos.purchase.api.response.PurchaseDetailResponse;
import com.eljaibo.pos.purchase.api.response.PurchaseItemResponse;
import com.eljaibo.pos.purchase.api.response.PurchasePaymentResponse;
import com.eljaibo.pos.purchase.api.response.PurchaseSummaryResponse;
import com.eljaibo.pos.purchase.domain.Purchase;
import com.eljaibo.pos.purchase.domain.PurchaseFinancialSummary;
import com.eljaibo.pos.purchase.domain.PurchaseItem;
import com.eljaibo.pos.purchase.domain.PurchasePaymentStatus;
import com.eljaibo.pos.purchase.domain.PurchaseStatus;
import com.eljaibo.pos.purchase.domain.Supplier;
import com.eljaibo.pos.purchase.persistence.PurchaseItemRepository;
import com.eljaibo.pos.purchase.persistence.PurchasePaymentRepository;
import com.eljaibo.pos.purchase.persistence.PurchaseRepository;
import com.eljaibo.pos.purchase.persistence.SupplierRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final PurchaseItemRepository items;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final TerminalRepository terminals;
    private final InventoryService inventoryService;
    private final SupplierRepository suppliers;
    private final PurchasePaymentRepository payments;
    private final Clock clock;

    public PurchaseService(
            PurchaseRepository purchases,
            PurchaseItemRepository items,
            ProductRepository products,
            AppUserRepository users,
            TerminalRepository terminals,
            InventoryService inventoryService,
            SupplierRepository suppliers,
            PurchasePaymentRepository payments,
            Clock clock
    ) {
        this.purchases = purchases;
        this.items = items;
        this.products = products;
        this.users = users;
        this.terminals = terminals;
        this.inventoryService = inventoryService;
        this.suppliers = suppliers;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public PurchaseDetailResponse create(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            CreatePurchaseRequest request
    ) {
        AppUser user =
                findUser(userId, businessId);

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Supplier supplier = null;

        if (request.supplierId() != null) {

            supplier = suppliers
                    .findByIdAndBusinessId(
                            request.supplierId(),
                            businessId
                    )
                    .filter(Supplier::isActive)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Active supplier not found"
                            )
                    );
        }

        Purchase purchase =
                purchases.save(
                        new Purchase(
                                terminal.getBranch(),
                                user,
                                supplier,
                                request.note()
                        )
                );

        return detailResponse(purchase);
    }

    @Transactional
    public PurchaseDetailResponse addItem(
            UUID businessId,
            UUID terminalId,
            UUID purchaseId,
            AddPurchaseItemRequest request
    ) {
        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Purchase purchase =
                purchases.findById(purchaseId)
                        .filter(value ->
                                value.getBranch()
                                        .getId()
                                        .equals(
                                                terminal.getBranch()
                                                        .getId()
                                        )
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Purchase not found"
                                )
                        );

        if (purchase.getStatus()
                != PurchaseStatus.DRAFT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a draft purchase can be modified"
            );
        }

        Product product =
                products.findByIdAndBusinessId(
                                request.productId(),
                                businessId
                        )
                        .filter(Product::isActive)
                        .filter(Product::isPurchasable)
                        .orElseThrow(
                                () -> notFound(
                                        "Active purchasable product not found"
                                )
                        );

        items.save(
                new PurchaseItem(
                        purchase,
                        product,
                        request.quantity(),
                        request.unitCost()
                )
        );

        recalculateTotal(purchase);

        return detailResponse(purchase);
    }

    private void recalculateTotal(
            Purchase purchase
    ) {
        BigDecimal total =
                items
                        .findAllByPurchaseIdOrderByIdAsc(
                                purchase.getId()
                        )
                        .stream()
                        .map(PurchaseItem::getSubtotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        purchase.updateTotal(total);
    }

    private PurchaseDetailResponse detailResponse(
            Purchase purchase
    ) {

        List<PurchaseItemResponse> purchaseItems =
                items.findAllByPurchaseIdOrderByIdAsc(
                                purchase.getId()
                        )
                        .stream()
                        .map(PurchaseItemResponse::from)
                        .toList();

        List<PurchasePaymentResponse> purchasePayments =
                payments.findAllByPurchaseIdOrderByPaidAtAscIdAsc(
                                purchase.getId()
                        )
                        .stream()
                        .map(PurchasePaymentResponse::from)
                        .toList();

        BigDecimal paidAmount =
                purchasePayments.stream()
                        .map(PurchasePaymentResponse::amount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        PurchaseFinancialSummary financial =
                PurchaseFinancialSummary.calculate(
                        purchase.getTotal(),
                        paidAmount
                );

        return PurchaseDetailResponse.from(
                purchase,
                purchaseItems,
                purchasePayments,
                financial
        );
    }

    private AppUser findUser(
            UUID userId,
            UUID businessId
    ) {
        return users.findById(userId)
                .filter(user ->
                        user.isActive()
                                && user.getBusiness()
                                .getId()
                                .equals(businessId)
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Active user required"
                        )
                );
    }

    @Transactional
    public PurchaseDetailResponse receive(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID purchaseId
    ) {
        AppUser user =
                findUser(
                        userId,
                        businessId
                );

        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Purchase purchase =
                purchases.findById(purchaseId)
                        .filter(value ->
                                value.getBranch()
                                        .getId()
                                        .equals(
                                                terminal
                                                        .getBranch()
                                                        .getId()
                                        )
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Purchase not found"
                                )
                        );

        if (purchase.getStatus()
                != PurchaseStatus.DRAFT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a draft purchase can be received"
            );
        }

        List<PurchaseItem> purchaseItems =
                items.findAllByPurchaseIdOrderByIdAsc(
                        purchase.getId()
                );

        if (purchaseItems.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Purchase must contain at least one item"
            );
        }

        Instant now = clock.instant();

        purchase.markReceived(now);

        inventoryService.receivePurchase(
                purchase,
                user,
                terminal
        );

        return detailResponse(purchase);
    }

    @Transactional
    public PurchaseDetailResponse removeItem(
            UUID businessId,
            UUID terminalId,
            UUID purchaseId,
            UUID itemId
    ) {
        Terminal terminal =
                findTerminal(
                        terminalId,
                        businessId
                );

        Purchase purchase =
                purchases.findById(purchaseId)
                        .filter(value ->
                                value.getBranch()
                                        .getId()
                                        .equals(
                                                terminal.getBranch().getId()
                                        )
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Purchase not found"
                                )
                        );

        if (purchase.getStatus()
                != PurchaseStatus.DRAFT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a draft purchase can be modified"
            );
        }

        PurchaseItem item =
                items.findById(itemId)
                        .filter(value ->
                                value.getPurchase()
                                        .getId()
                                        .equals(purchaseId)
                        )
                        .orElseThrow(
                                () -> notFound(
                                        "Purchase item not found"
                                )
                        );

        items.delete(item);

        items.flush();

        recalculateTotal(purchase);

        return detailResponse(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseDetailResponse findById(
            UUID businessId,
            UUID terminalId,
            UUID purchaseId
    ) {

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        Purchase purchase = purchases.findById(purchaseId)
                .filter(value ->
                        value.getBranch()
                                .getId()
                                .equals(
                                        terminal.getBranch().getId()
                                )
                )
                .orElseThrow(() ->
                        notFound("Purchase not found")
                );

        return detailResponse(purchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseSummaryResponse> findAll(
            UUID businessId,
            UUID terminalId,
            PurchaseStatus status,
            PurchasePaymentStatus paymentStatus,
            UUID supplierId,
            boolean pendingOnly
    ) {

        Terminal terminal = findTerminal(
                terminalId,
                businessId
        );

        List<Purchase> purchaseList =
                purchases.findAllByBranchIdOrderByCreatedAtDesc(
                        terminal.getBranch().getId()
                );

        return purchaseList.stream()

                .filter(purchase ->
                        status == null
                                || purchase.getStatus() == status
                )

                .filter(purchase ->
                        supplierId == null
                                || (
                                purchase.getSupplier() != null
                                        && purchase.getSupplier()
                                        .getId()
                                        .equals(supplierId)
                        )
                )

                .map(purchase -> {

                    BigDecimal paidAmount =
                            payments.sumAmountByPurchaseId(
                                    purchase.getId()
                            );

                    PurchaseFinancialSummary financial =
                            PurchaseFinancialSummary.calculate(
                                    purchase.getTotal(),
                                    paidAmount
                            );

                    return PurchaseSummaryResponse.from(
                            purchase,
                            financial
                    );
                })

                .filter(purchase ->
                        paymentStatus == null
                                || purchase.paymentStatus() == paymentStatus
                )

                .filter(purchase ->
                        !pendingOnly
                                || (
                                purchase.status() == PurchaseStatus.RECEIVED
                                        && purchase.pendingBalance().signum() > 0
                        )
                )

                .toList();
    }

    private Terminal findTerminal(
            UUID terminalId,
            UUID businessId
    ) {
        return terminals
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(
                        () -> notFound(
                                "Active terminal not found"
                        )
                );
    }

    private ResponseStatusException notFound(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}