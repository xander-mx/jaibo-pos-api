package com.eljaibo.pos.auth.security;

import com.eljaibo.pos.user.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final UUID businessId;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final UserRole role;
    private final boolean active;

    public AuthenticatedUser(
            UUID id,
            UUID businessId,
            String username,
            String passwordHash,
            String displayName,
            UserRole role,
            boolean active
    ) {
        this.id = id;
        this.businessId = businessId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }
}