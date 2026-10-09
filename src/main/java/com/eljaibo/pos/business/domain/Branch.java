package com.eljaibo.pos.business.domain;

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

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "branch")
public class Branch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Branch() {
        // Requerido por JPA.
    }

    public Branch(Business business, String code, String name) {
        if (business == null) {
            throw new IllegalArgumentException("business is required");
        }

        this.business = business;
        this.code = required(code, "code").toUpperCase(Locale.ROOT);
        this.name = required(name, "name");
        this.active = true;
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
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
}