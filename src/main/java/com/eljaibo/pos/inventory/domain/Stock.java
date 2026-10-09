package com.eljaibo.pos.inventory.domain;

import com.eljaibo.pos.business.domain.Branch;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(
        name = "stock",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_branch_product",
                        columnNames = {
                                "branch_id",
                                "product_id"
                        }
                )
        }
)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantity;

    @Version
    @Column(nullable = false)
    private long version;

    protected Stock() {
        // Requerido por JPA.
    }

    public Stock(
            Branch branch,
            Product product
    ) {
        if (branch == null) {
            throw new IllegalArgumentException(
                    "branch is required"
            );
        }

        if (product == null) {
            throw new IllegalArgumentException(
                    "product is required"
            );
        }

        if (!branch.getBusiness()
                .getId()
                .equals(product.getBusiness().getId())) {

            throw new IllegalArgumentException(
                    "Branch and product must belong to the same business"
            );
        }

        this.branch = branch;
        this.product = product;
        this.quantity = BigDecimal.ZERO
                .setScale(3, RoundingMode.HALF_UP);
    }

    public void setQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException(
                    "quantity is required"
            );
        }

        this.quantity = quantity.setScale(
                3,
                RoundingMode.HALF_UP
        );
    }

    public UUID getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version;
    }
}