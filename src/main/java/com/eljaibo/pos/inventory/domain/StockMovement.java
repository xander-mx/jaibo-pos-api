package com.eljaibo.pos.inventory.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "movement_type",
            nullable = false,
            length = 30
    )
    private StockMovementType movementType;

    @Column(
            name = "previous_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal previousQuantity;

    @Column(
            name = "new_quantity",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal newQuantity;

    @Column(
            name = "quantity_change",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal quantityChange;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private AppUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private Instant occurredAt;

    protected StockMovement() {
        // Requerido por JPA.
    }

    public StockMovement(
            Stock stock,
            StockMovementType movementType,
            BigDecimal previousQuantity,
            BigDecimal newQuantity,
            String reason,
            AppUser performedBy,
            Terminal terminal
    ) {
        if (stock == null) {
            throw new IllegalArgumentException(
                    "stock is required"
            );
        }

        if (movementType == null) {
            throw new IllegalArgumentException(
                    "movementType is required"
            );
        }

        if (previousQuantity == null || newQuantity == null) {
            throw new IllegalArgumentException(
                    "Quantities are required"
            );
        }

        if (performedBy == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }

        this.stock = stock;
        this.movementType = movementType;

        this.previousQuantity =
                previousQuantity.setScale(
                        3,
                        RoundingMode.HALF_UP
                );

        this.newQuantity =
                newQuantity.setScale(
                        3,
                        RoundingMode.HALF_UP
                );

        this.quantityChange =
                this.newQuantity
                        .subtract(this.previousQuantity)
                        .setScale(
                                3,
                                RoundingMode.HALF_UP
                        );

        this.reason =
                reason == null || reason.isBlank()
                        ? null
                        : reason.trim();

        this.performedBy = performedBy;
        this.terminal = terminal;
        this.occurredAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Stock getStock() {
        return stock;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getPreviousQuantity() {
        return previousQuantity;
    }

    public BigDecimal getNewQuantity() {
        return newQuantity;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getPerformedBy() {
        return performedBy;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}