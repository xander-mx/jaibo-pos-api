package com.eljaibo.pos.purchase.domain;

import com.eljaibo.pos.catalog.domain.Product;
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
@Table(name = "purchase_item")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantity;

    @Column(
            name = "unit_cost",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal unitCost;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal subtotal;

    protected PurchaseItem() {
        // Requerido por JPA.
    }

    public PurchaseItem(
            Purchase purchase,
            Product product,
            BigDecimal quantity,
            BigDecimal unitCost
    ) {
        if (purchase == null) {
            throw new IllegalArgumentException(
                    "purchase is required"
            );
        }

        if (product == null) {
            throw new IllegalArgumentException(
                    "product is required"
            );
        }

        if (!product.isActive()
                || !product.isPurchasable()) {
            throw new IllegalArgumentException(
                    "Product must be active and purchasable"
            );
        }

        if (!purchase.getBranch()
                .getBusiness()
                .getId()
                .equals(product.getBusiness().getId())) {

            throw new IllegalArgumentException(
                    "Purchase and product must belong to the same business"
            );
        }

        if (quantity == null
                || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive"
            );
        }

        if (unitCost == null
                || unitCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "unitCost cannot be negative"
            );
        }

        this.purchase = purchase;
        this.product = product;

        this.quantity =
                quantity.setScale(
                        3,
                        RoundingMode.HALF_UP
                );

        this.unitCost =
                unitCost.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        this.subtotal =
                this.quantity
                        .multiply(this.unitCost)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );
    }

    public UUID getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}