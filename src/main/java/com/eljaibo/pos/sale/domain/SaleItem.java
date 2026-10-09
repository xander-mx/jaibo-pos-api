package com.eljaibo.pos.sale.domain;

import com.eljaibo.pos.catalog.domain.Product;
import com.eljaibo.pos.catalog.domain.ProductPresentation;
import com.eljaibo.pos.catalog.domain.Unit;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_item")
public class SaleItem extends AuditableEntity {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private ProductPresentation presentation;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(
            name = "presentation_name",
            nullable = false,
            length = 100
    )
    private String presentationName;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "unit_symbol", nullable = false, length = 15)
    private String unitSymbol;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(
            name = "conversion_factor",
            nullable = false,
            precision = 14,
            scale = 6
    )
    private BigDecimal conversionFactor;

    @Column(
            name = "unit_price",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal unitPrice;

    @Column(
            name = "price_basis_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal priceBasisQuantity;

    @Column(
            name = "gross_amount",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(
            name = "discount_value",
            nullable = false,
            precision = 14,
            scale = 4
    )
    private BigDecimal discountValue;

    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal discountAmount;

    @Column(
            name = "line_total",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private boolean courtesy;

    @Column(nullable = false)
    private boolean removed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_from_terminal_id", nullable = false)
    private Terminal createdFromTerminal;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_user_id")
    private AppUser removedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_from_terminal_id")
    private Terminal removedFromTerminal;

    @Version
    @Column(nullable = false)
    private long version;

    protected SaleItem() {
        // Requerido por JPA.
    }

    public SaleItem(
            Sale sale,
            ProductPresentation presentation,
            BigDecimal quantity,
            BigDecimal unitPrice,
            AppUser createdBy,
            Terminal createdFromTerminal
    ) {
        validateCreation(
                sale,
                presentation,
                createdBy,
                createdFromTerminal
        );

        Product selectedProduct = presentation.getProduct();
        Unit selectedUnit = presentation.getUnit();

        this.sale = sale;
        this.product = selectedProduct;
        this.presentation = presentation;
        this.productName = selectedProduct.getName();
        this.presentationName = presentation.getName();
        this.unitCode = selectedUnit.getCode();
        this.unitSymbol = selectedUnit.getSymbol();
        this.quantity = normalizeQuantity(
                quantity,
                presentation
        );
        this.conversionFactor = presentation
                .getConversionFactor()
                .setScale(6, RoundingMode.HALF_UP);
        this.unitPrice = nonNegativeMoney(
                unitPrice,
                "unitPrice"
        );
        this.priceBasisQuantity = presentation
                .getPriceBasisQuantity()
                .setScale(3, RoundingMode.HALF_UP);
        this.discountType = DiscountType.NONE;
        this.discountValue = zeroDiscount();
        this.discountAmount = zeroMoney();
        this.courtesy = false;
        this.removed = false;
        this.createdBy = createdBy;
        this.createdFromTerminal = createdFromTerminal;

        recalculate();
    }

    public void changeQuantity(BigDecimal quantity) {
        ensureEditable();

        this.quantity = normalizeQuantity(
                quantity,
                presentation
        );

        recalculate();
    }

    public void changePrice(BigDecimal unitPrice) {
        ensureEditable();

        this.unitPrice = nonNegativeMoney(
                unitPrice,
                "unitPrice"
        );

        recalculate();
    }

    public void applyDiscount(
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        ensureEditable();

        if (discountType == null
                || discountType == DiscountType.NONE) {
            removeDiscount();
            return;
        }

        BigDecimal normalizedValue = nonNegativeDiscount(
                discountValue,
                "discountValue"
        );

        if (discountType == DiscountType.PERCENT
                && normalizedValue.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "Percentage cannot exceed 100"
            );
        }

        this.courtesy = false;
        this.discountType = discountType;
        this.discountValue = normalizedValue;

        recalculate();
    }

    public void removeDiscount() {
        ensureEditable();

        discountType = DiscountType.NONE;
        discountValue = zeroDiscount();
        discountAmount = zeroMoney();

        recalculate();
    }

    public void markCourtesy() {
        ensureEditable();

        courtesy = true;
        discountType = DiscountType.NONE;
        discountValue = zeroDiscount();
        discountAmount = zeroMoney();

        recalculate();
    }

    public void removeCourtesy() {
        ensureEditable();
        courtesy = false;
        recalculate();
    }

    public void remove(
            AppUser user,
            Terminal terminal
    ) {
        ensureEditable();

        if (user == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }

        removed = true;
        removedAt = Instant.now();
        removedBy = user;
        removedFromTerminal = terminal;
    }

    public BigDecimal calculateInventoryQuantity() {
        return quantity
                .multiply(conversionFactor)
                .setScale(3, RoundingMode.HALF_UP);
    }

    public void moveTo(Sale targetSale) {
        ensureEditable();

        if (targetSale == null) {
            throw new IllegalArgumentException(
                    "Target sale is required"
            );
        }

        if (targetSale.getStatus() != SaleStatus.OPEN) {
            throw new IllegalStateException(
                    "Target sale must be open"
            );
        }

        if (sale.getId().equals(targetSale.getId())) {
            throw new IllegalArgumentException(
                    "Source and target sale must be different"
            );
        }

        if (!sale.getBusiness().getId()
                .equals(targetSale.getBusiness().getId())) {
            throw new IllegalArgumentException(
                    "Sales belong to different businesses"
            );
        }

        if (!sale.getBranch().getId()
                .equals(targetSale.getBranch().getId())) {
            throw new IllegalArgumentException(
                    "Sales belong to different branches"
            );
        }

        sale = targetSale;
    }

    public SaleItem splitTo(
            Sale targetSale,
            BigDecimal splitQuantity,
            AppUser user,
            Terminal terminal
    ) {
        ensureEditable();

        if (targetSale == null) {
            throw new IllegalArgumentException(
                    "Target sale is required"
            );
        }

        if (targetSale.getStatus() != SaleStatus.OPEN) {
            throw new IllegalStateException(
                    "Target sale must be open"
            );
        }

        if (sale.getId().equals(targetSale.getId())) {
            throw new IllegalArgumentException(
                    "Source and target sale must be different"
            );
        }

        if (!sale.getBusiness().getId()
                .equals(targetSale.getBusiness().getId())) {
            throw new IllegalArgumentException(
                    "Sales belong to different businesses"
            );
        }

        if (!sale.getBranch().getId()
                .equals(targetSale.getBranch().getId())) {
            throw new IllegalArgumentException(
                    "Sales belong to different branches"
            );
        }

        if (user == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }

        if (discountType == DiscountType.AMOUNT) {
            throw new IllegalStateException(
                    "Item with fixed discount cannot be partially moved"
            );
        }

        BigDecimal normalizedSplit =
                normalizeQuantity(
                        splitQuantity,
                        presentation
                );

        if (normalizedSplit.compareTo(quantity) >= 0) {
            throw new IllegalArgumentException(
                    "Split quantity must be less than item quantity"
            );
        }

        BigDecimal remainingQuantity =
                quantity.subtract(normalizedSplit);

        /*
         * También validamos que la cantidad que queda
         * siga siendo válida para la presentación.
         */
        BigDecimal normalizedRemaining =
                normalizeQuantity(
                        remainingQuantity,
                        presentation
                );

        SaleItem targetItem = new SaleItem();

        targetItem.sale = targetSale;
        targetItem.product = product;
        targetItem.presentation = presentation;

        targetItem.productName = productName;
        targetItem.presentationName = presentationName;
        targetItem.unitCode = unitCode;
        targetItem.unitSymbol = unitSymbol;

        targetItem.quantity = normalizedSplit;
        targetItem.conversionFactor = conversionFactor;
        targetItem.unitPrice = unitPrice;
        targetItem.priceBasisQuantity = priceBasisQuantity;

        targetItem.discountType = discountType;
        targetItem.discountValue = discountValue;

        targetItem.courtesy = courtesy;
        targetItem.removed = false;

        targetItem.createdBy = user;
        targetItem.createdFromTerminal = terminal;

        targetItem.recalculate();

        quantity = normalizedRemaining;
        recalculate();

        return targetItem;
    }

    private void recalculate() {
        grossAmount = quantity
                .divide(
                        priceBasisQuantity,
                        6,
                        RoundingMode.HALF_UP
                )
                .multiply(unitPrice)
                .setScale(2, RoundingMode.HALF_UP);

        discountAmount = switch (discountType) {
            case NONE -> zeroMoney();

            case AMOUNT -> discountValue
                    .setScale(2, RoundingMode.HALF_UP)
                    .min(grossAmount);

            case PERCENT -> grossAmount
                    .multiply(discountValue)
                    .divide(
                            ONE_HUNDRED,
                            2,
                            RoundingMode.HALF_UP
                    );
        };

        lineTotal = courtesy
                ? zeroMoney()
                : grossAmount
                .subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureEditable() {
        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new IllegalStateException(
                    "Only items from an open sale can be modified"
            );
        }

        if (removed) {
            throw new IllegalStateException(
                    "Removed item cannot be modified"
            );
        }
    }

    private static void validateCreation(
            Sale sale,
            ProductPresentation presentation,
            AppUser user,
            Terminal terminal
    ) {
        if (sale == null || sale.getStatus() != SaleStatus.OPEN) {
            throw new IllegalArgumentException(
                    "An open sale is required"
            );
        }

        if (presentation == null || !presentation.isActive()) {
            throw new IllegalArgumentException(
                    "An active presentation is required"
            );
        }

        Product product = presentation.getProduct();

        if (!product.isActive() || !product.isSellable()) {
            throw new IllegalArgumentException(
                    "An active sellable product is required"
            );
        }

        if (!product.getBusiness().getId()
                .equals(sale.getBusiness().getId())) {
            throw new IllegalArgumentException(
                    "Product belongs to another business"
            );
        }

        if (user == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }
    }

    private static BigDecimal normalizeQuantity(
            BigDecimal value,
            ProductPresentation presentation
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive"
            );
        }

        if (!presentation.isVariableQuantity()
                && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "This presentation requires a whole quantity"
            );
        }

        int suppliedDecimals = Math.max(
                0,
                value.stripTrailingZeros().scale()
        );

        if (suppliedDecimals
                > presentation.getUnit().getDecimalPlaces()) {
            throw new IllegalArgumentException(
                    "Quantity has more decimal places than its unit allows"
            );
        }

        BigDecimal minimum = presentation.getMinimumQuantity();

        if (minimum != null && value.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(
                    "Quantity is below the presentation minimum"
            );
        }

        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeMoney(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeDiscount(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroMoney() {
        return new BigDecimal("0.00");
    }

    private static BigDecimal zeroDiscount() {
        return new BigDecimal("0.0000");
    }

    public UUID getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public Product getProduct() {
        return product;
    }

    public ProductPresentation getPresentation() {
        return presentation;
    }

    public String getProductName() {
        return productName;
    }

    public String getPresentationName() {
        return presentationName;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public String getUnitSymbol() {
        return unitSymbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getPriceBasisQuantity() {
        return priceBasisQuantity;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public boolean isCourtesy() {
        return courtesy;
    }

    public boolean isRemoved() {
        return removed;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Terminal getCreatedFromTerminal() {
        return createdFromTerminal;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public AppUser getRemovedBy() {
        return removedBy;
    }

    public Terminal getRemovedFromTerminal() {
        return removedFromTerminal;
    }

    public long getVersion() {
        return version;
    }
}