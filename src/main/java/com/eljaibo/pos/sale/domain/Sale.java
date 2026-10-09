package com.eljaibo.pos.sale.domain;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.cash.domain.CashSession;
import com.eljaibo.pos.cash.domain.CashSessionStatus;
import com.eljaibo.pos.customer.domain.Customer;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale")
public class Sale extends AuditableEntity {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession cashSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_number", nullable = false)
    private int saleNumber;

    @Column(length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(
            name = "line_discount_total",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal lineDiscountTotal;

    @Column(
            name = "courtesy_total",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal courtesyTotal;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "sale_discount_type",
            nullable = false,
            length = 20
    )
    private DiscountType saleDiscountType;

    @Column(
            name = "sale_discount_value",
            nullable = false,
            precision = 14,
            scale = 4
    )
    private BigDecimal saleDiscountValue;

    @Column(
            name = "sale_discount_amount",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal saleDiscountAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_user_id", nullable = false)
    private AppUser openedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_from_terminal_id", nullable = false)
    private Terminal openedFromTerminal;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by_user_id")
    private AppUser finalizedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_from_terminal_id")
    private Terminal finalizedFromTerminal;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private AppUser cancelledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_from_terminal_id")
    private Terminal cancelledFromTerminal;

    @Version
    @Column(nullable = false)
    private long version;

    protected Sale() {
        // Requerido por JPA.
    }

    public Sale(
            CashSession cashSession,
            int saleNumber,
            String reference,
            AppUser openedBy,
            Terminal openedFromTerminal
    ) {
        validateOpening(
                cashSession,
                saleNumber,
                openedBy,
                openedFromTerminal
        );

        this.cashSession = cashSession;
        this.branch = cashSession
                .getOperatingCycle()
                .getBranch();
        this.business = branch.getBusiness();
        this.saleNumber = saleNumber;
        this.reference = optional(reference);
        this.status = SaleStatus.OPEN;
        this.subtotal = zeroMoney();
        this.lineDiscountTotal = zeroMoney();
        this.courtesyTotal = zeroMoney();
        this.saleDiscountType = DiscountType.NONE;
        this.saleDiscountValue = zeroDiscount();
        this.saleDiscountAmount = zeroMoney();
        this.total = zeroMoney();
        this.openedBy = openedBy;
        this.openedFromTerminal = openedFromTerminal;
    }

    public void changeReference(String reference) {
        ensureOpen();
        this.reference = optional(reference);
    }

    public void assignCustomer(Customer customer) {
        ensureOpen();

        if (customer == null || !customer.isActive()) {
            throw new IllegalArgumentException(
                    "An active customer is required"
            );
        }

        if (!customer.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException(
                    "Customer belongs to another business"
            );
        }

        this.customer = customer;
    }

    public void removeCustomer() {
        ensureOpen();
        customer = null;
    }

    public void refreshTotals(
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            BigDecimal courtesyTotal
    ) {
        ensureOpen();

        BigDecimal normalizedSubtotal =
                nonNegativeMoney(subtotal, "subtotal");

        BigDecimal normalizedLineDiscount =
                nonNegativeMoney(
                        lineDiscountTotal,
                        "lineDiscountTotal"
                );

        BigDecimal normalizedCourtesy =
                nonNegativeMoney(
                        courtesyTotal,
                        "courtesyTotal"
                );

        if (normalizedLineDiscount
                .add(normalizedCourtesy)
                .compareTo(normalizedSubtotal) > 0) {
            throw new IllegalArgumentException(
                    "Discounts and courtesies exceed subtotal"
            );
        }

        this.subtotal = normalizedSubtotal;
        this.lineDiscountTotal = normalizedLineDiscount;
        this.courtesyTotal = normalizedCourtesy;

        recalculateSaleDiscount();
    }

    public void applySaleDiscount(
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        ensureOpen();

        if (discountType == null
                || discountType == DiscountType.NONE) {
            removeSaleDiscount();
            return;
        }

        BigDecimal normalizedValue = nonNegativeDiscount(
                discountValue,
                "discountValue"
        );

        if (discountType == DiscountType.PERCENT
                && normalizedValue.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "Percentage cannot exceed 100"
            );
        }

        saleDiscountType = discountType;
        saleDiscountValue = normalizedValue;
        recalculateSaleDiscount();
    }

    public void removeSaleDiscount() {
        ensureOpen();
        saleDiscountType = DiscountType.NONE;
        saleDiscountValue = zeroDiscount();
        saleDiscountAmount = zeroMoney();
        recalculateTotal();
    }

    public void markPaid(
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        ensureOpen();

        if (user == null || terminal == null || now == null) {
            throw new IllegalArgumentException(
                    "User, terminal and finalized time are required"
            );
        }

        if (!terminal.getBranch().getId().equals(branch.getId())) {
            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        status = SaleStatus.PAID;
        finalizedAt = now;
        finalizedBy = user;
        finalizedFromTerminal = terminal;
    }

    public void markCredit(
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        ensureOpen();

        if (customer == null) {
            throw new IllegalStateException(
                    "A customer is required for a credit sale"
            );
        }

        if (user == null || terminal == null || now == null) {
            throw new IllegalArgumentException(
                    "User, terminal and finalized time are required"
            );
        }

        if (!terminal.getBranch().getId().equals(branch.getId())) {
            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        status = SaleStatus.CREDIT;
        finalizedAt = now;
        finalizedBy = user;
        finalizedFromTerminal = terminal;
    }

    public void cancel(
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        ensureOpen();

        if (user == null || terminal == null || now == null) {
            throw new IllegalArgumentException(
                    "User, terminal and finalized time are required"
            );
        }

        if (!terminal.getBranch().getId().equals(branch.getId())) {
            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }

        status = SaleStatus.CANCELLED;
        finalizedAt = now;
        finalizedBy = user;
        finalizedFromTerminal = terminal;
    }

    private void recalculateSaleDiscount() {
        BigDecimal availableAmount = subtotal
                .subtract(lineDiscountTotal)
                .subtract(courtesyTotal);

        saleDiscountAmount = switch (saleDiscountType) {
            case NONE -> zeroMoney();

            case AMOUNT -> saleDiscountValue
                    .setScale(2, RoundingMode.HALF_UP)
                    .min(availableAmount);

            case PERCENT -> availableAmount
                    .multiply(saleDiscountValue)
                    .divide(
                            ONE_HUNDRED,
                            2,
                            RoundingMode.HALF_UP
                    );
        };

        recalculateTotal();
    }

    private void recalculateTotal() {
        total = subtotal
                .subtract(lineDiscountTotal)
                .subtract(courtesyTotal)
                .subtract(saleDiscountAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureOpen() {
        if (status != SaleStatus.OPEN) {
            throw new IllegalStateException(
                    "Only an open sale can be modified"
            );
        }
    }

    private static void validateOpening(
            CashSession cashSession,
            int saleNumber,
            AppUser user,
            Terminal terminal
    ) {
        if (cashSession == null
                || cashSession.getStatus() != CashSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                    "An open cash session is required"
            );
        }

        if (saleNumber <= 0) {
            throw new IllegalArgumentException(
                    "saleNumber must be positive"
            );
        }

        if (user == null || terminal == null) {
            throw new IllegalArgumentException(
                    "User and terminal are required"
            );
        }

        UUID branchId = cashSession
                .getOperatingCycle()
                .getBranch()
                .getId();

        if (!terminal.getBranch().getId().equals(branchId)) {
            throw new IllegalArgumentException(
                    "Terminal belongs to another branch"
            );
        }
    }

    private static BigDecimal nonNegativeMoney(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeDiscount(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroMoney() {
        return new BigDecimal("0.00");
    }

    private static BigDecimal zeroDiscount() {
        return new BigDecimal("0.0000");
    }

    private static String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public Branch getBranch() {
        return branch;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public Customer getCustomer() {
        return customer;
    }

    public int getSaleNumber() {
        return saleNumber;
    }

    public String getReference() {
        return reference;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getLineDiscountTotal() {
        return lineDiscountTotal;
    }

    public BigDecimal getCourtesyTotal() {
        return courtesyTotal;
    }

    public DiscountType getSaleDiscountType() {
        return saleDiscountType;
    }

    public BigDecimal getSaleDiscountValue() {
        return saleDiscountValue;
    }

    public BigDecimal getSaleDiscountAmount() {
        return saleDiscountAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public AppUser getOpenedBy() {
        return openedBy;
    }

    public Terminal getOpenedFromTerminal() {
        return openedFromTerminal;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public AppUser getFinalizedBy() {
        return finalizedBy;
    }

    public Terminal getFinalizedFromTerminal() {
        return finalizedFromTerminal;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public AppUser getCancelledBy() {
        return cancelledBy;
    }

    public Terminal getCancelledFromTerminal() {
        return cancelledFromTerminal;
    }

    public long getVersion() {
        return version;
    }
}