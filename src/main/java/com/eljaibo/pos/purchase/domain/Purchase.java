package com.eljaibo.pos.purchase.domain;

import com.eljaibo.pos.business.domain.Branch;
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
@Table(name = "purchase")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal total;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    protected Purchase() {
        // Requerido por JPA.
    }

    public Purchase(
            Branch branch,
            AppUser createdBy,
            Supplier supplier,
            String note
    ) {
        if (branch == null) {
            throw new IllegalArgumentException(
                    "branch is required"
            );
        }

        if (createdBy == null) {
            throw new IllegalArgumentException(
                    "createdBy is required"
            );
        }

        UUID businessId = branch.getBusiness().getId();

        if (!businessId.equals(
                createdBy.getBusiness().getId()
        )) {
            throw new IllegalArgumentException(
                    "Branch and user must belong to the same business"
            );
        }

        if (supplier != null) {

            if (!businessId.equals(
                    supplier.getBusiness().getId()
            )) {
                throw new IllegalArgumentException(
                        "Supplier belongs to another business"
                );
            }

            if (!supplier.isActive()) {
                throw new IllegalArgumentException(
                        "Supplier must be active"
                );
            }
        }

        this.branch = branch;
        this.createdBy = createdBy;
        this.supplier = supplier;

        this.note = note == null || note.isBlank()
                ? null
                : note.trim();

        this.status = PurchaseStatus.DRAFT;

        this.total = BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );

        this.createdAt = Instant.now();
    }

    public void updateTotal(
            BigDecimal total
    ) {
        if (total == null ||
                total.signum() < 0) {
            throw new IllegalArgumentException(
                    "total cannot be negative"
            );
        }

        this.total = total.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    public void markReceived(
            Instant now
    ) {
        if (status != PurchaseStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a draft purchase can be received"
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "received time is required"
            );
        }

        status = PurchaseStatus.RECEIVED;
        receivedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getNote() {
        return note;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Supplier getSupplier() {
        return supplier;
    }
}