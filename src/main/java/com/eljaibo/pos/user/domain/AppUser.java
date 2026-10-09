package com.eljaibo.pos.user.domain;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.shared.persistence.AuditableEntity;
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

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
        // Requerido por JPA.
    }

    public AppUser(
            Business business,
            String username,
            String passwordHash,
            String displayName,
            UserRole role
    ) {
        if (business == null) {
            throw new IllegalArgumentException("business is required");
        }

        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }

        this.business = business;
        this.username = required(username, "username")
                .toLowerCase(Locale.ROOT);
        this.passwordHash = required(passwordHash, "passwordHash");
        this.displayName = required(displayName, "displayName");
        this.role = role;
        this.active = true;
    }

    public void registerSuccessfulLogin() {
        lastLoginAt = Instant.now();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = required(passwordHash, "passwordHash");
        credentialVersion = Math.incrementExact(credentialVersion);
    }

    public long getCredentialVersion() { return credentialVersion; }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }
}
