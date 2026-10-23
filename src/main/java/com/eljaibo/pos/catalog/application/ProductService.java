package com.eljaibo.pos.catalog.application;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.catalog.api.CreateProductRequest;
import com.eljaibo.pos.catalog.api.ProductResponse;
import com.eljaibo.pos.catalog.api.UnitResponse;
import com.eljaibo.pos.catalog.api.UpdateProductRequest;
import com.eljaibo.pos.catalog.domain.Category;
import com.eljaibo.pos.catalog.domain.InventoryMode;
import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.domain.Unit;
import com.eljaibo.pos.catalog.persistence.CategoryRepository;
import com.eljaibo.pos.catalog.persistence.ProductRepository;
import com.eljaibo.pos.catalog.persistence.UnitRepository;
import com.eljaibo.pos.inventory.persistence.StockMovementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {

    private final BusinessRepository businessRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(
            BusinessRepository businessRepository,
            CategoryRepository categoryRepository,
            UnitRepository unitRepository,
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.businessRepository = businessRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public ProductResponse create(
            UUID businessId,
            CreateProductRequest request
    ) {
        Business business = findBusiness(businessId);
        Category category = findCategory(
                request.categoryId(),
                businessId
        );
        Unit inventoryUnit = findInventoryUnit(
                request.inventoryUnitId(),
                request.inventoryMode()
        );

        if (productRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId,
                request.name()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A product with this name already exists"
            );
        }

        int displayOrder = request.displayOrder() == null
                ? 0
                : request.displayOrder();

        Product product = new Product(
                business,
                category,
                inventoryUnit,
                request.name(),
                request.description(),
                request.inventoryMode(),
                request.sellable(),
                request.purchasable(),
                displayOrder
        );

        product.changeImage(request.imageUrl());

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findActive(
            UUID businessId,
            boolean sellableOnly,
            boolean purchasableOnly
    ) {
        List<Product> products;

        if (sellableOnly) {
            products =
                    productRepository
                            .findAllByBusinessIdAndSellableTrueAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
                                    businessId
                            );
        } else if (purchasableOnly) {
            products =
                    productRepository
                            .findAllByBusinessIdAndPurchasableTrueAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
                                    businessId
                            );
        } else {
            products =
                    productRepository
                            .findAllByBusinessIdAndActiveTrueOrderByCategoryDisplayOrderAscDisplayOrderAscNameAsc(
                                    businessId
                            );
        }

        return products.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse update(
            UUID businessId,
            UUID productId,
            UpdateProductRequest request
    ) {
        Product product = productRepository
                .findByIdAndBusinessId(
                        productId,
                        businessId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found"
                        )
                );

        Category category = findCategory(
                request.categoryId(),
                businessId
        );

        Unit inventoryUnit = findInventoryUnit(
                request.inventoryUnitId(),
                request.inventoryMode()
        );

        int displayOrder =
                request.displayOrder() == null
                        ? 0
                        : request.displayOrder();

        if (productRepository
                .existsByBusinessIdAndNameIgnoreCaseAndIdNot(
                        businessId,
                        request.name(),
                        productId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un producto con este nombre"
            );
        }

        product.changeGeneralInformation(
                request.name(),
                request.description(),
                category,
                displayOrder
        );

        UUID currentInventoryUnitId =
                product.getInventoryUnit() == null
                        ? null
                        : product.getInventoryUnit().getId();

        boolean inventoryConfigurationChanged =
                product.getInventoryMode() != request.inventoryMode()
                        || !Objects.equals(
                        currentInventoryUnitId,
                        request.inventoryUnitId()
                );

        if (inventoryConfigurationChanged
                && stockMovementRepository.existsByStockProductId(productId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La configuracción de inventario no puede cambiar porque este producto tiene movimientos de inventario actuales"
            );
        }

        product.configureInventory(
                request.inventoryMode(),
                inventoryUnit
        );

        product.configureUsage(
                request.sellable(),
                request.purchasable()
        );

        product.changeImage(
                request.imageUrl()
        );

        return toResponse(product);
    }

    private Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .filter(Business::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active business not found"
                ));
    }

    private Category findCategory(
            UUID categoryId,
            UUID businessId
    ) {
        return categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .filter(Category::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active category not found"
                ));
    }

    private Unit findInventoryUnit(
            UUID unitId,
            InventoryMode inventoryMode
    ) {
        if (unitId == null) {
            if (inventoryMode != InventoryMode.NOT_TRACKED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Controlled product requires an inventory unit"
                );
            }

            return null;
        }

        return unitRepository.findById(unitId)
                .filter(Unit::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active inventory unit not found"
                ));
    }

    private ProductResponse toResponse(Product product) {
        Unit inventoryUnit = product.getInventoryUnit();

        UnitResponse unitResponse = inventoryUnit == null
                ? null
                : new UnitResponse(
                inventoryUnit.getId(),
                inventoryUnit.getCode(),
                inventoryUnit.getName(),
                inventoryUnit.getSymbol(),
                inventoryUnit.getDecimalPlaces()
        );

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getInventoryMode(),
                product.isSellable(),
                product.isPurchasable(),
                product.getDisplayOrder(),
                product.isActive(),
                new ProductResponse.Category(
                        product.getCategory().getId(),
                        product.getCategory().getName()
                ),
                unitResponse
        );
    }
}