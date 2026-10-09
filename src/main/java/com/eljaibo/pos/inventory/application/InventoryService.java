package com.eljaibo.pos.inventory.application;

import com.eljaibo.pos.catalog.domain.InventoryMode;
import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.persistence.ProductRepository;
import com.eljaibo.pos.inventory.api.AdjustInventoryRequest;
import com.eljaibo.pos.inventory.api.InventoryResponse;
import com.eljaibo.pos.inventory.api.RegisterWasteRequest;
import com.eljaibo.pos.inventory.domain.Stock;
import com.eljaibo.pos.inventory.domain.StockMovement;
import com.eljaibo.pos.inventory.domain.StockMovementType;
import com.eljaibo.pos.inventory.persistence.StockMovementRepository;
import com.eljaibo.pos.inventory.persistence.StockRepository;
import com.eljaibo.pos.purchase.domain.Purchase;
import com.eljaibo.pos.purchase.domain.PurchaseItem;
import com.eljaibo.pos.purchase.persistence.PurchaseItemRepository;
import com.eljaibo.pos.sale.domain.Sale;
import com.eljaibo.pos.sale.domain.SaleItem;
import com.eljaibo.pos.sale.persistence.SaleItemRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final TerminalRepository terminalRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AppUserRepository appUserRepository;
    private final SaleItemRepository saleItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;

    public InventoryService(
            ProductRepository productRepository,
            StockRepository stockRepository,
            TerminalRepository terminalRepository,
            StockMovementRepository stockMovementRepository,
            AppUserRepository appUserRepository,
            SaleItemRepository saleItemRepository,
            PurchaseItemRepository purchaseItemRepository
    ) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.terminalRepository = terminalRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.appUserRepository = appUserRepository;
        this.saleItemRepository = saleItemRepository;
        this.purchaseItemRepository = purchaseItemRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findInventory(
            UUID businessId,
            UUID terminalId
    ) {
        Terminal terminal = terminalRepository
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active terminal not found"
                        )
                );

        UUID branchId = terminal
                .getBranch()
                .getId();

        return productRepository
                .findAllByBusinessIdAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
                        businessId
                )
                .stream()
                .filter(product ->
                        product.getInventoryMode()
                                != InventoryMode.NOT_TRACKED
                )
                .map(product ->
                        toResponse(
                                branchId,
                                product
                        )
                )
                .toList();
    }

    @Transactional
    public void receivePurchase(
            Purchase purchase,
            AppUser user,
            Terminal terminal
    ) {
        List<PurchaseItem> items =
                purchaseItemRepository
                        .findAllByPurchaseIdOrderByIdAsc(
                                purchase.getId()
                        );

        for (PurchaseItem item : items) {
            Product product = item.getProduct();

            if (product.getInventoryMode()
                    == InventoryMode.NOT_TRACKED) {
                continue;
            }

            Stock stock =
                    stockRepository
                            .findByBranchIdAndProductId(
                                    purchase.getBranch().getId(),
                                    product.getId()
                            )
                            .orElseGet(() ->
                                    stockRepository.save(
                                            new Stock(
                                                    purchase.getBranch(),
                                                    product
                                            )
                                    )
                            );

            BigDecimal previousQuantity =
                    stock.getQuantity();

            BigDecimal newQuantity =
                    previousQuantity
                            .add(item.getQuantity());

            stock.setQuantity(newQuantity);

            stockMovementRepository.save(
                    new StockMovement(
                            stock,
                            StockMovementType.PURCHASE,
                            previousQuantity,
                            newQuantity,
                            "Compra " + purchase.getId(),
                            user,
                            terminal
                    )
            );
        }
    }

    private InventoryResponse toResponse(
            UUID branchId,
            Product product
    ) {
        BigDecimal quantity = stockRepository
                .findByBranchIdAndProductId(
                        branchId,
                        product.getId()
                )
                .map(Stock::getQuantity)
                .orElse(
                        BigDecimal.ZERO.setScale(
                                3,
                                RoundingMode.HALF_UP
                        )
                );

        var unit = product.getInventoryUnit();

        return new InventoryResponse(
                product.getId(),
                product.getName(),
                product.getInventoryMode(),
                unit.getId(),
                unit.getCode(),
                unit.getSymbol(),
                quantity
        );
    }

    @Transactional
    public InventoryResponse adjustQuantity(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID productId,
            AdjustInventoryRequest request
    ) {
        Terminal terminal = terminalRepository
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active terminal not found"
                        )
                );

        AppUser user = appUserRepository
                .findById(userId)
                .filter(value ->
                        value.isActive()
                                && value.getBusiness()
                                .getId()
                                .equals(businessId)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user is not active"
                        )
                );

        Product product = productRepository
                .findByIdAndBusinessId(
                        productId,
                        businessId
                )
                .filter(Product::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active product not found"
                        )
                );

        if (product.getInventoryMode()
                == InventoryMode.NOT_TRACKED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product does not track inventory"
            );
        }

        Stock stock = stockRepository
                .findByBranchIdAndProductId(
                        terminal.getBranch().getId(),
                        product.getId()
                )
                .orElseGet(() ->
                        stockRepository.save(
                                new Stock(
                                        terminal.getBranch(),
                                        product
                                )
                        )
                );

        BigDecimal previousQuantity =
                stock.getQuantity();

        stock.setQuantity(
                request.quantity()
        );

        stockMovementRepository.save(
                new StockMovement(
                        stock,
                        StockMovementType.ADJUSTMENT,
                        previousQuantity,
                        stock.getQuantity(),
                        request.reason(),
                        user,
                        terminal
                )
        );

        return toResponse(
                terminal.getBranch().getId(),
                product
        );
    }

    @Transactional
    public InventoryResponse registerWaste(
            UUID userId,
            UUID businessId,
            UUID terminalId,
            UUID productId,
            RegisterWasteRequest request
    ) {
        Terminal terminal = terminalRepository
                .findByIdAndBranchBusinessId(
                        terminalId,
                        businessId
                )
                .filter(Terminal::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active terminal not found"
                        )
                );

        AppUser user = appUserRepository
                .findById(userId)
                .filter(value ->
                        value.isActive()
                                && value.getBusiness()
                                .getId()
                                .equals(businessId)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user is not active"
                        )
                );

        Product product = productRepository
                .findByIdAndBusinessId(
                        productId,
                        businessId
                )
                .filter(Product::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Active product not found"
                        )
                );

        if (product.getInventoryMode()
                == InventoryMode.NOT_TRACKED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product does not track inventory"
            );
        }

        Stock stock = stockRepository
                .findByBranchIdAndProductId(
                        terminal.getBranch().getId(),
                        product.getId()
                )
                .orElseGet(() ->
                        stockRepository.save(
                                new Stock(
                                        terminal.getBranch(),
                                        product
                                )
                        )
                );

        BigDecimal previousQuantity =
                stock.getQuantity();

        BigDecimal newQuantity =
                previousQuantity.subtract(
                        request.quantity()
                );

        stock.setQuantity(newQuantity);

        stockMovementRepository.save(
                new StockMovement(
                        stock,
                        StockMovementType.WASTE,
                        previousQuantity,
                        stock.getQuantity(),
                        request.reason(),
                        user,
                        terminal
                )
        );

        return toResponse(
                terminal.getBranch().getId(),
                product
        );
    }

    @Transactional
    public void consumeSale(
            Sale sale,
            AppUser user,
            Terminal terminal
    ) {
        List<SaleItem> items =
                saleItemRepository
                        .findAllBySaleIdAndRemovedFalseOrderByCreatedAtAsc(
                                sale.getId()
                        );

        for (SaleItem item : items) {

            Product product =
                    item.getProduct();

            if (product.getInventoryMode()
                    != InventoryMode.DIRECT) {
                continue;
            }

            Stock stock = stockRepository
                    .findByBranchIdAndProductId(
                            sale.getBranch().getId(),
                            product.getId()
                    )
                    .orElseGet(() ->
                            stockRepository.save(
                                    new Stock(
                                            sale.getBranch(),
                                            product
                                    )
                            )
                    );

            BigDecimal previousQuantity =
                    stock.getQuantity();

            BigDecimal consumedQuantity =
                    item.calculateInventoryQuantity();

            BigDecimal newQuantity =
                    previousQuantity.subtract(
                            consumedQuantity
                    );

            stock.setQuantity(newQuantity);

            stockMovementRepository.save(
                    new StockMovement(
                            stock,
                            StockMovementType.SALE,
                            previousQuantity,
                            newQuantity,
                            "Venta #" + sale.getSaleNumber(),
                            user,
                            terminal
                    )
            );
        }
    }
}