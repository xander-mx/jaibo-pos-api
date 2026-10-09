package com.eljaibo.pos.catalog.domain;

import com.eljaibo.pos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "product_presentation")
public class ProductPresentation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(
            name = "conversion_factor",
            nullable = false,
            precision = 14,
            scale = 6
    )
    private BigDecimal conversionFactor;

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "variable_quantity", nullable = false)
    private boolean variableQuantity;

    @Column(name = "minimum_quantity", precision = 14, scale = 3)
    private BigDecimal minimumQuantity;

    @Column(name = "open_price", nullable = false)
    private boolean openPrice;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "price_basis_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal priceBasisQuantity;

    protected ProductPresentation() {
        // Requerido por JPA.
    }

    public ProductPresentation(
            Product product,
            Unit unit,
            String name,
            BigDecimal conversionFactor,
            BigDecimal salePrice,
            boolean variableQuantity,
            BigDecimal minimumQuantity,
            boolean openPrice,
            int displayOrder,
            BigDecimal priceBasisQuantity
    ) {
        validate(
                product,
                unit,
                conversionFactor,
                salePrice,
                minimumQuantity,
                openPrice,
                displayOrder,
                priceBasisQuantity
        );

        this.product = product;
        this.unit = unit;
        this.name = required(name, "name");
        this.conversionFactor = conversionFactor.setScale(
                6,
                RoundingMode.HALF_UP
        );
        this.salePrice = moneyOrNull(salePrice);
        this.variableQuantity = variableQuantity;
        this.minimumQuantity = quantityOrNull(minimumQuantity);
        this.openPrice = openPrice;
        this.displayOrder = displayOrder;
        this.active = true;
        this.priceBasisQuantity = positiveQuantity(
                priceBasisQuantity,
                "priceBasisQuantity"
        );
    }

    public void changePricing(
            BigDecimal salePrice,
            BigDecimal priceBasisQuantity,
            boolean openPrice
    ) {
        validatePrice(salePrice, openPrice);

        this.salePrice = moneyOrNull(salePrice);
        this.priceBasisQuantity = positiveQuantity(
                priceBasisQuantity,
                "priceBasisQuantity"
        );
        this.openPrice = openPrice;
    }

    public void configureQuantity(
            boolean variableQuantity,
            BigDecimal minimumQuantity
    ) {
        if (minimumQuantity != null
                && minimumQuantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "minimumQuantity must be positive"
            );
        }

        this.variableQuantity = variableQuantity;
        this.minimumQuantity = quantityOrNull(minimumQuantity);
    }

    public void changeDisplayOrder(int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException(
                    "displayOrder cannot be negative"
            );
        }

        this.displayOrder = displayOrder;
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Unit getUnit() {
        return unit;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public boolean isVariableQuantity() {
        return variableQuantity;
    }

    public BigDecimal getMinimumQuantity() {
        return minimumQuantity;
    }

    public boolean isOpenPrice() {
        return openPrice;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getPriceBasisQuantity() {
        return priceBasisQuantity;
    }

    public void setPriceBasisQuantity(BigDecimal priceBasisQuantity) {
        this.priceBasisQuantity = priceBasisQuantity;
    }

    private static void validate(
            Product product,
            Unit unit,
            BigDecimal conversionFactor,
            BigDecimal salePrice,
            BigDecimal minimumQuantity,
            boolean openPrice,
            int displayOrder,
            BigDecimal priceBasisQuantity
    ) {
        if (product == null) {
            throw new IllegalArgumentException("product is required");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit is required");
        }
        if (conversionFactor == null
                || conversionFactor.signum() <= 0) {
            throw new IllegalArgumentException(
                    "conversionFactor must be positive"
            );
        }
        if (minimumQuantity != null
                && minimumQuantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "minimumQuantity must be positive"
            );
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException(
                    "displayOrder cannot be negative"
            );
        }
        positiveQuantity(
                priceBasisQuantity,
                "priceBasisQuantity"
        );
        validatePrice(salePrice, openPrice);
    }

    private static void validatePrice(
            BigDecimal salePrice,
            boolean openPrice
    ) {
        if (!openPrice && salePrice == null) {
            throw new IllegalArgumentException(
                    "Fixed price presentation requires a sale price"
            );
        }
        if (salePrice != null && salePrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "salePrice cannot be negative"
            );
        }
    }

    private static BigDecimal moneyOrNull(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantityOrNull(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(3, RoundingMode.HALF_UP);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }

    private static BigDecimal positiveQuantity(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    field + " must be positive"
            );
        }

        return value.setScale(3, RoundingMode.HALF_UP);
    }
}