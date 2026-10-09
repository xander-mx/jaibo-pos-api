package com.eljaibo.pos.terminal.domain;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
import com.eljaibo.pos.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terminal")
public class Terminal extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by_user_id", nullable = false)
    private AppUser registeredBy;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Terminal() {
        // Requerido por JPA.
    }

    public Terminal(
            Branch branch,
            AppUser registeredBy,
            String name
    ) {
        if (branch == null) {
            throw new IllegalArgumentException("branch is required");
        }
        if (registeredBy == null) {
            throw new IllegalArgumentException(
                    "registeredBy is required"
            );
        }

        this.branch = branch;
        this.registeredBy = registeredBy;
        this.name = required(name, "name");
        this.active = true;
        this.lastSeenAt = Instant.now();
    }

    public void rename(String name) {
        this.name = required(name, "name");
    }

    public void markSeen() {
        lastSeenAt = Instant.now();
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

    public Branch getBranch() {
        return branch;
    }

    public AppUser getRegisteredBy() {
        return registeredBy;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }
}