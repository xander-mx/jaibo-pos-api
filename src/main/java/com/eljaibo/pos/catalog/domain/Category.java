package com.eljaibo.pos.catalog.domain;

import com.eljaibo.pos.business.domain.Business;
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

import java.util.UUID;

@Entity
@Table(name = "category")
public class Category extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    protected Category() {
        // Requerido por JPA.
    }

    public Category(
            Business business,
            String name,
            int displayOrder
    ) {
        if (business == null) {
            throw new IllegalArgumentException("business is required");
        }

        this.business = business;
        this.name = required(name, "name");
        this.displayOrder = nonNegative(
                displayOrder,
                "displayOrder"
        );
        this.active = true;
    }

    public void rename(String name) {
        this.name = required(name, "name");
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = nonNegative(
                displayOrder,
                "displayOrder"
        );
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

    public String getName() {
        return name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return value;
    }
}