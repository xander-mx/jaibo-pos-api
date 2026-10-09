package com.eljaibo.pos.cash.domain;

import com.eljaibo.pos.cycle.domain.OperatingCycle;
import com.eljaibo.pos.cycle.domain.OperatingCycleStatus;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cash_session")
public class CashSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operating_cycle_id", nullable = false)
    private OperatingCycle operatingCycle;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSessionStatus status;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_user_id", nullable = false)
    private AppUser openedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name ="opened_from_terminal_id", nullable = false)
    private Terminal openedFromTerminal;

    @Column(name = "closing_expected_amount", precision = 14, scale = 2)
    private BigDecimal closingExpectedAmount;

    @Column(name = "closing_counted_amount", precision = 14, scale = 2)
    private BigDecimal closingCountedAmount;

    @Column(name = "closing_difference", precision = 14, scale = 2)
    private BigDecimal closingDifference;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private AppUser closedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_from_terminal_id")
    private Terminal closedFromTerminal;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    protected CashSession() {
        // Requerido por JPA.
    }

    public CashSession(
            OperatingCycle operatingCycle,
            LocalDate businessDate,
            BigDecimal openingAmount,
            AppUser openedBy,
            Terminal openedFromTerminal
    ) {
        validateOpening(
                operatingCycle,
                businessDate,
                openedBy,
                openedFromTerminal
        );

        this.operatingCycle = operatingCycle;
        this.businessDate = businessDate;
        this.openingAmount = nonNegativeMoney(
                openingAmount,
                "openingAmount"
        );
        this.openedBy = openedBy;
        this.openedFromTerminal = openedFromTerminal;
        this.openedAt = Instant.now();
        this.status = CashSessionStatus.OPEN;
        this.reopenCount = 0;
    }

    public void close(
            BigDecimal expectedAmount,
            BigDecimal countedAmount,
            AppUser user,
            Terminal terminal
    ) {
        requireAdministrator(user);

        if (status != CashSessionStatus.OPEN) {
            throw new IllegalStateException(
                    "Only an open cash session can be closed"
            );
        }
        if (terminal == null) {
            throw new IllegalArgumentException("terminal is required");
        }

        closingExpectedAmount = money(
                expectedAmount,
                "expectedAmount"
        );
        closingCountedAmount = nonNegativeMoney(
                countedAmount,
                "countedAmount"
        );
        closingDifference = closingCountedAmount.subtract(
                closingExpectedAmount
        );
        closedAt = Instant.now();
        closedBy = user;
        closedFromTerminal = terminal;
        status = CashSessionStatus.CLOSED;
    }

    public void reopen(AppUser user) {
        requireAdministrator(user);

        if (status != CashSessionStatus.CLOSED) {
            throw new IllegalStateException(
                    "Only a closed cash session can be reopened"
            );
        }

        status = CashSessionStatus.OPEN;
        closingExpectedAmount = null;
        closingCountedAmount = null;
        closingDifference = null;
        closedAt = null;
        closedBy = null;
        closedFromTerminal = null;
        reopenCount++;
    }

    public UUID getId() {
        return id;
    }

    public OperatingCycle getOperatingCycle() {
        return operatingCycle;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public CashSessionStatus getStatus() {
        return status;
    }

    public BigDecimal getOpeningAmount() {
        return openingAmount;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public AppUser getOpenedBy() {
        return openedBy;
    }

    public Terminal getOpenedFromTerminal() {
        return openedFromTerminal;
    }

    public BigDecimal getClosingExpectedAmount() {
        return closingExpectedAmount;
    }

    public BigDecimal getClosingCountedAmount() {
        return closingCountedAmount;
    }

    public BigDecimal getClosingDifference() {
        return closingDifference;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public AppUser getClosedBy() {
        return closedBy;
    }

    public Terminal getClosedFromTerminal() {
        return closedFromTerminal;
    }

    public int getReopenCount() {
        return reopenCount;
    }

    private static void validateOpening(
            OperatingCycle cycle,
            LocalDate businessDate,
            AppUser user,
            Terminal terminal
    ) {
        if (cycle == null) {
            throw new IllegalArgumentException(
                    "operatingCycle is required"
            );
        }
        if (cycle.getStatus() != OperatingCycleStatus.OPEN) {
            throw new IllegalArgumentException(
                    "operatingCycle must be open"
            );
        }
        if (businessDate == null) {
            throw new IllegalArgumentException(
                    "businessDate is required"
            );
        }
        if (businessDate.isBefore(cycle.getStartsOn())
                || businessDate.isAfter(cycle.getEndsOn())) {
            throw new IllegalArgumentException(
                    "businessDate is outside the operating cycle"
            );
        }
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (terminal == null) {
            throw new IllegalArgumentException("terminal is required");
        }
    }

    private static void requireAdministrator(AppUser user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException(
                    "Administrator role is required"
            );
        }
    }

    private static BigDecimal nonNegativeMoney(
            BigDecimal value,
            String field
    ) {
        BigDecimal amount = money(value, field);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return amount;
    }

    private static BigDecimal money(
            BigDecimal value,
            String field
    ) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }
}