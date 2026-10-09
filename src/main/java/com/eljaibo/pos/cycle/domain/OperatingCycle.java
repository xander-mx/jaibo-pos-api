package com.eljaibo.pos.cycle.domain;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "operating_cycle")
public class OperatingCycle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperatingCycleStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private AppUser closedBy;

    protected OperatingCycle() {
        // Requerido por JPA.
    }

    public static OperatingCycle open(
            Branch branch,
            LocalDate startsOn
    ) {
        return new OperatingCycle(
                branch,
                startsOn,
                OperatingCycleStatus.OPEN
        );
    }

    public static OperatingCycle planned(
            Branch branch,
            LocalDate startsOn
    ) {
        return new OperatingCycle(
                branch,
                startsOn,
                OperatingCycleStatus.PLANNED
        );
    }

    private OperatingCycle(
            Branch branch,
            LocalDate startsOn,
            OperatingCycleStatus status
    ) {
        if (branch == null) {
            throw new IllegalArgumentException("branch is required");
        }
        if (startsOn == null) {
            throw new IllegalArgumentException("startsOn is required");
        }
        if (startsOn.getDayOfWeek() != DayOfWeek.FRIDAY) {
            throw new IllegalArgumentException(
                    "Operating cycle must start on Friday"
            );
        }

        this.branch = branch;
        this.startsOn = startsOn;
        this.endsOn = startsOn.plusDays(6);
        this.status = status;
    }

    public void activate() {
        if (status != OperatingCycleStatus.PLANNED) {
            throw new IllegalStateException(
                    "Only a planned cycle can be activated"
            );
        }

        status = OperatingCycleStatus.OPEN;
    }

    public void close(AppUser user) {
        if (status != OperatingCycleStatus.OPEN) {
            throw new IllegalStateException(
                    "Only an open cycle can be closed"
            );
        }
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        status = OperatingCycleStatus.CLOSED;
        closedAt = Instant.now();
        closedBy = user;
    }

    public UUID getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public OperatingCycleStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public AppUser getClosedBy() {
        return closedBy;
    }
}