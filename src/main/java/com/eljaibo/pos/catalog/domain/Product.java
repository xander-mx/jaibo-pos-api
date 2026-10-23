package com.eljaibo.pos.catalog.domain;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
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

import java.util.UUID;

@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_unit_id")
    private Unit inventoryUnit;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_mode", nullable = false, length = 30)
    private InventoryMode inventoryMode;

    @Column(nullable = false)
    private boolean sellable;

    @Column(nullable = false)
    private boolean purchasable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    protected Product() {
        // Requerido por JPA.
    }

    public Product(
            Business business,
            Category category,
            Unit inventoryUnit,
            String name,
            String description,
            InventoryMode inventoryMode,
            boolean sellable,
            boolean purchasable,
            int displayOrder
    ) {
        validate(
                business,
                category,
                inventoryUnit,
                inventoryMode,
                sellable,
                purchasable,
                displayOrder
        );

        this.business = business;
        this.category = category;
        this.inventoryUnit = inventoryUnit;
        this.name = required(name, "name");
        this.description = optional(description);
        this.inventoryMode = inventoryMode;
        this.sellable = sellable;
        this.purchasable = purchasable;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public void changeGeneralInformation(
            String name,
            String description,
            Category category,
            int displayOrder
    ) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException(
                    "displayOrder cannot be negative"
            );
        }

        this.name = required(name, "name");
        this.description = optional(description);
        this.category = category;
        this.displayOrder = displayOrder;
    }

    public void configureInventory(
            InventoryMode inventoryMode,
            Unit inventoryUnit
    ) {
        validateInventory(inventoryMode, inventoryUnit);

        validateUsage(
                sellable,
                purchasable,
                inventoryMode
        );

        this.inventoryMode = inventoryMode;
        this.inventoryUnit = inventoryUnit;
    }

    public void configureUsage(
            boolean sellable,
            boolean purchasable
    ) {
        validateUsage(
                sellable,
                purchasable,
                inventoryMode
        );

        this.sellable = sellable;
        this.purchasable = purchasable;
    }

    public void changeImage(String imageUrl) {
        this.imageUrl = optional(imageUrl);
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

    public Business getBusiness() {
        return business;
    }

    public Category getCategory() {
        return category;
    }

    public Unit getInventoryUnit() {
        return inventoryUnit;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public boolean isSellable() {
        return sellable;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    private static void validate(
            Business business,
            Category category,
            Unit inventoryUnit,
            InventoryMode inventoryMode,
            boolean sellable,
            boolean purchasable,
            int displayOrder
    ) {
        if (business == null) {
            throw new IllegalArgumentException("business is required");
        }
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        validateUsage(
                sellable,
                purchasable,
                inventoryMode
        );
        if (displayOrder < 0) {
            throw new IllegalArgumentException(
                    "displayOrder cannot be negative"
            );
        }

        validateInventory(inventoryMode, inventoryUnit);
    }

    private static void validateInventory(
            InventoryMode inventoryMode,
            Unit inventoryUnit
    ) {
        if (inventoryMode == null) {
            throw new IllegalArgumentException(
                    "inventoryMode is required"
            );
        }

        if (inventoryMode != InventoryMode.NOT_TRACKED
                && inventoryUnit == null) {
            throw new IllegalArgumentException(
                    "Controlled product requires an inventory unit"
            );
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private static void validateUsage(
            boolean sellable,
            boolean purchasable,
            InventoryMode inventoryMode
    ) {
        if (!sellable
                && !purchasable
                && inventoryMode == InventoryMode.NOT_TRACKED) {
            throw new IllegalArgumentException(
                    "Product must be sellable, purchasable or inventory tracked"
            );
        }
    }
}