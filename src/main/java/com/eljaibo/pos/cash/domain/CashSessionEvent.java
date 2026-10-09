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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cash_session_event")
public class CashSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private CashSessionEventType eventType;

    @Column(name = "expected_amount", precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "counted_amount", precision = 14, scale = 2)
    private BigDecimal countedAmount;

    @Column(precision = 14, scale = 2)
    private BigDecimal difference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private AppUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @Column(length = 255)
    private String comment;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CashSessionEvent() {
        // Requerido por JPA.
    }

    public static CashSessionEvent opened(
            CashSession cashSession,
            AppUser user,
            Terminal terminal
    ) {
        return new CashSessionEvent(
                cashSession,
                CashSessionEventType.OPENED,
                null,
                null,
                null,
                user,
                terminal,
                null
        );
    }

    public static CashSessionEvent closed(
            CashSession cashSession,
            AppUser user,
            Terminal terminal
    ) {
        return new CashSessionEvent(
                cashSession,
                CashSessionEventType.CLOSED,
                cashSession.getClosingExpectedAmount(),
                cashSession.getClosingCountedAmount(),
                cashSession.getClosingDifference(),
                user,
                terminal,
                null
        );
    }

    public static CashSessionEvent reopened(
            CashSession cashSession,
            AppUser user,
            Terminal terminal,
            String comment
    ) {
        return new CashSessionEvent(
                cashSession,
                CashSessionEventType.REOPENED,
                null,
                null,
                null,
                user,
                terminal,
                optional(comment)
        );
    }

    private CashSessionEvent(
            CashSession cashSession,
            CashSessionEventType eventType,
            BigDecimal expectedAmount,
            BigDecimal countedAmount,
            BigDecimal difference,
            AppUser performedBy,
            Terminal terminal,
            String comment
    ) {
        if (cashSession == null) {
            throw new IllegalArgumentException(
                    "cashSession is required"
            );
        }
        if (eventType == null) {
            throw new IllegalArgumentException(
                    "eventType is required"
            );
        }
        if (performedBy == null) {
            throw new IllegalArgumentException(
                    "performedBy is required"
            );
        }
        if (terminal == null) {
            throw new IllegalArgumentException(
                    "terminal is required"
            );
        }

        this.cashSession = cashSession;
        this.eventType = eventType;
        this.expectedAmount = expectedAmount;
        this.countedAmount = countedAmount;
        this.difference = difference;
        this.performedBy = performedBy;
        this.terminal = terminal;
        this.comment = comment;
        this.occurredAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public CashSessionEventType getEventType() {
        return eventType;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getCountedAmount() {
        return countedAmount;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public AppUser getPerformedBy() {
        return performedBy;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}