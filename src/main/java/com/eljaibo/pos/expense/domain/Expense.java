package com.eljaibo.pos.expense.domain;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.sale.domain.PaymentMethod;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id")
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(nullable = false, length = 150)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_source", nullable = false, length = 20)
    private ExpensePaymentSource paymentSource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_from_terminal_id", nullable = false)
    private Terminal createdFromTerminal;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected Expense() {
    }

    public Expense(
            Branch branch,
            CashSession cashSession,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            ExpensePaymentSource paymentSource,
            AppUser createdBy,
            Terminal createdFromTerminal,
            Instant occurredAt
    ) {
        if (branch == null) {
            throw new IllegalArgumentException("Branch is required");
        }

        if (category == null) {
            throw new IllegalArgumentException("Expense category is required");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Expense description is required");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        if (paymentSource == null) {
            throw new IllegalArgumentException("Payment source is required");
        }

        if (createdBy == null || createdFromTerminal == null) {
            throw new IllegalArgumentException("User and terminal are required");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException("Expense time is required");
        }

        if (paymentSource == ExpensePaymentSource.CASH_REGISTER
                && cashSession == null) {
            throw new IllegalArgumentException(
                    "Cash session is required when expense is paid from cash register"
            );
        }

        if (paymentSource == ExpensePaymentSource.CASH_REGISTER
                && paymentMethod != PaymentMethod.CASH) {

            throw new IllegalArgumentException(
                    "Cash register expenses must use cash payment method"
            );
        }

        this.branch = branch;
        this.cashSession = cashSession;
        this.category = category;
        this.description = description.trim();
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.paymentMethod = paymentMethod;
        this.paymentSource = paymentSource;
        this.createdBy = createdBy;
        this.createdFromTerminal = createdFromTerminal;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public ExpensePaymentSource getPaymentSource() {
        return paymentSource;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Terminal getCreatedFromTerminal() {
        return createdFromTerminal;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}