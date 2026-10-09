package com.eljaibo.pos.cash.domain;

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
@Table(name = "cash_movement")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashMovementDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private CashMovementType movementType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private AppUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CashMovement() {
        // Requerido por JPA.
    }

    public CashMovement(
            CashSession cashSession,
            CashMovementDirection direction,
            CashMovementType movementType,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            String description,
            AppUser performedBy,
            Terminal terminal
    ) {
        validate(
                cashSession,
                direction,
                movementType,
                amount,
                referenceType,
                referenceId,
                performedBy,
                terminal
        );

        this.cashSession = cashSession;
        this.direction = direction;
        this.movementType = movementType;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.referenceType = optional(referenceType);
        this.referenceId = referenceId;
        this.description = optional(description);
        this.performedBy = performedBy;
        this.terminal = terminal;
        this.occurredAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        createdAt = Instant.now();
    }

    private static void validate(
            CashSession cashSession,
            CashMovementDirection direction,
            CashMovementType movementType,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            AppUser user,
            Terminal terminal
    ) {
        if (cashSession == null
                || cashSession.getStatus() != CashSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                    "An open cash session is required"
            );
        }
        if (direction == null || movementType == null) {
            throw new IllegalArgumentException(
                    "Movement direction and type are required"
            );
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Movement amount must be positive"
            );
        }
        if (user == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }

        boolean hasReferenceType =
                referenceType != null && !referenceType.isBlank();
        boolean hasReferenceId = referenceId != null;

        if (hasReferenceType != hasReferenceId) {
            throw new IllegalArgumentException(
                    "Reference type and id must be provided together"
            );
        }

        validateDirection(direction, movementType);
    }

    private static void validateDirection(
            CashMovementDirection direction,
            CashMovementType type
    ) {
        if (type == CashMovementType.ADJUSTMENT) {
            return;
        }

        boolean validIncome = direction == CashMovementDirection.IN
                && switch (type) {
            case SALE_PAYMENT,
                 CUSTOMER_PAYMENT,
                 TRANSFER_PAYMENT,
                 CASH_IN -> true;
            default -> false;
        };

        boolean validOutcome = direction == CashMovementDirection.OUT
                && switch (type) {
            case PURCHASE_PAYMENT,
                 EXPENSE,
                 REFUND,
                 CASH_OUT -> true;
            default -> false;
        };

        if (!validIncome && !validOutcome) {
            throw new IllegalArgumentException(
                    "Movement type does not match its direction"
            );
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public CashMovementDirection getDirection() {
        return direction;
    }

    public CashMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}