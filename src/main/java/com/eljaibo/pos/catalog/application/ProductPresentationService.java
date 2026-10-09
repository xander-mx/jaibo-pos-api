package com.eljaibo.pos.catalog.application;

import com.eljaibo.pos.catalog.api.CreateProductPresentationRequest;
import com.eljaibo.pos.catalog.api.ProductPresentationResponse;
import com.eljaibo.pos.catalog.api.UnitResponse;
import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.domain.ProductPresentation;
import com.eljaibo.pos.catalog.domain.Unit;
import com.eljaibo.pos.catalog.persistence.ProductPresentationRepository;
import com.eljaibo.pos.catalog.persistence.ProductRepository;
import com.eljaibo.pos.catalog.persistence.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProductPresentationService {

    private static final Logger logger = LoggerFactory.getLogger(ProductPresentationService.class);

    private final ProductRepository productRepository;
    private final ProductPresentationRepository presentationRepository;
    private final UnitRepository unitRepository;

    public ProductPresentationService(
            ProductRepository productRepository,
            ProductPresentationRepository presentationRepository,
            UnitRepository unitRepository
    ) {
        this.productRepository = productRepository;
        this.presentationRepository = presentationRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional
    public ProductPresentationResponse create(
            UUID businessId,
            UUID productId,
            CreateProductPresentationRequest request
    ) {
        Product product = findSellableProduct(
                businessId,
                productId
        );

        Unit unit = unitRepository.findById(request.unitId())
                .filter(Unit::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active unit not found"
                ));

        if (presentationRepository
                .existsByProductIdAndNameIgnoreCase(
                        productId,
                        request.name()
                )) {
            logger.warn(
                    "Duplicate product presentation rejected: productId={}, name={}",
                    productId,
                    request.name()
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A presentation with this name already exists"
            );
        }

        int displayOrder = request.displayOrder() == null
                ? 0
                : request.displayOrder();

        ProductPresentation presentation =
                new ProductPresentation(
                        product,
                        unit,
                        request.name(),
                        request.conversionFactor(),
                        request.salePrice(),
                        request.variableQuantity(),
                        request.minimumQuantity(),
                        request.openPrice(),
                        displayOrder,
                        request.priceBasisQuantity()
                );

        logger.info(
                "Product presentation created: presentationId={}, productId={}, name={}",
                presentation.getId(),
                productId,
                presentation.getName()
        );

        return toResponse(
                presentationRepository.save(presentation)
        );
    }

    @Transactional(readOnly = true)
    public List<ProductPresentationResponse> findActive(
            UUID businessId,
            UUID productId
    ) {
        findProduct(businessId, productId);

        return presentationRepository
                .findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(
                        productId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Product findSellableProduct(
            UUID businessId,
            UUID productId
    ) {
        Product product = findProduct(businessId, productId);

        if (!product.isSellable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Presentations can only be added to sellable products"
            );
        }

        return product;
    }

    private Product findProduct(
            UUID businessId,
            UUID productId
    ) {
        return productRepository
                .findByIdAndBusinessId(productId, businessId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active product not found"
                ));
    }

    private ProductPresentationResponse toResponse(
            ProductPresentation presentation
    ) {
        Unit unit = presentation.getUnit();

        return new ProductPresentationResponse(
                presentation.getId(),
                presentation.getProduct().getId(),
                presentation.getName(),
                new UnitResponse(
                        unit.getId(),
                        unit.getCode(),
                        unit.getName(),
                        unit.getSymbol(),
                        unit.getDecimalPlaces()
                ),
                presentation.getConversionFactor(),
                presentation.getSalePrice(),
                presentation.getPriceBasisQuantity(),
                presentation.isVariableQuantity(),
                presentation.getMinimumQuantity(),
                presentation.isOpenPrice(),
                presentation.getDisplayOrder(),
                presentation.isActive()
        );
    }
}