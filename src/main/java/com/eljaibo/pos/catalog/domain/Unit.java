package com.eljaibo.pos.catalog.domain;

import com.eljaibo.pos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "unit")
public class Unit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 15)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private short decimalPlaces;

    @Column(nullable = false)
    private boolean active;

    protected Unit() {
        // Requerido por JPA.
    }

    public Unit(
            String code,
            String name,
            String symbol,
            int decimalPlaces
    ) {
        if (decimalPlaces < 0 || decimalPlaces > 3) {
            throw new IllegalArgumentException(
                    "decimalPlaces must be between 0 and 3"
            );
        }

        this.code = required(code, "code")
                .toUpperCase(Locale.ROOT);
        this.name = required(name, "name");
        this.symbol = required(symbol, "symbol");
        this.decimalPlaces = (short) decimalPlaces;
        this.active = true;
    }

    public void rename(String name, String symbol) {
        this.name = required(name, "name");
        this.symbol = required(symbol, "symbol");
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
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