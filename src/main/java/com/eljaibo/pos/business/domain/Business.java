package com.eljaibo.pos.business.domain;

import com.eljaibo.pos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "business")
public class Business extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "legal_name", length = 180)
    private String legalName;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private boolean active;

    protected Business() {
        // Requerido por JPA.
    }

    public Business(
            String name,
            String legalName,
            String currencyCode,
            String timezone
    ) {
        this.name = required(name, "name");
        this.legalName = optional(legalName);
        this.currencyCode = normalizeCurrency(currencyCode);
        this.timezone = validateTimezone(timezone);
        this.active = true;
    }

    public void changeLogo(String logoUrl) {
        this.logoUrl = optional(logoUrl);
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

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public boolean isActive() {
        return active;
    }

    private static String normalizeCurrency(String value) {
        String currency = required(value, "currencyCode")
                .toUpperCase(Locale.ROOT);

        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "currencyCode must contain three characters"
            );
        }

        return currency;
    }

    private static String validateTimezone(String value) {
        return required(value, "timezone");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}