package com.eljaibo.pos.purchase.domain;

import com.eljaibo.pos.business.domain.Business;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    protected Supplier() {
        // Requerido por JPA.
    }

    public Supplier(
            Business business,
            String name,
            String phone
    ) {
        if (business == null) {
            throw new IllegalArgumentException(
                    "business is required"
            );
        }

        this.business = business;
        this.name = required(name);
        this.phone = optional(phone);
        this.active = true;
    }

    public void update(
            String name,
            String phone
    ) {
        this.name = required(name);
        this.phone = optional(phone);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Supplier name is required"
            );
        }

        String normalized = value
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.length() > 120) {
            throw new IllegalArgumentException(
                    "Supplier name cannot exceed 120 characters"
            );
        }

        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > 30) {
            throw new IllegalArgumentException(
                    "Supplier phone cannot exceed 30 characters"
            );
        }

        return normalized;
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

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}