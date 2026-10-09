package com.eljaibo.pos.customer.domain;

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

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer extends AuditableEntity {

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

    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean frequent;

    @Column(nullable = false)
    private boolean wholesale;

    @Column(name = "credit_enabled", nullable = false)
    private boolean creditEnabled;

    @Column(nullable = false)
    private boolean active;

    protected Customer() {
        // Requerido por JPA.
    }

    public Customer(
            Business business,
            String name,
            String phone,
            String email,
            boolean frequent,
            boolean wholesale,
            boolean creditEnabled
    ) {
        if (business == null) {
            throw new IllegalArgumentException(
                    "business is required"
            );
        }

        this.business = business;
        this.name = required(name, "name");
        this.phone = optional(phone);
        this.email = normalizeEmail(email);
        this.frequent = frequent;
        this.wholesale = wholesale;
        this.creditEnabled = creditEnabled;
        this.active = true;
    }

    public void changeContactInformation(
            String name,
            String phone,
            String email
    ) {
        this.name = required(name, "name");
        this.phone = optional(phone);
        this.email = normalizeEmail(email);
    }

    public void configureClassification(
            boolean frequent,
            boolean wholesale
    ) {
        this.frequent = frequent;
        this.wholesale = wholesale;
    }

    public void enableCredit() {
        creditEnabled = true;
    }

    public void disableCredit() {
        creditEnabled = false;
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isFrequent() {
        return frequent;
    }

    public boolean isWholesale() {
        return wholesale;
    }

    public boolean isCreditEnabled() {
        return creditEnabled;
    }

    public boolean isActive() {
        return active;
    }

    private static String normalizeEmail(String value) {
        String email = optional(value);

        return email == null
                ? null
                : email.toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}