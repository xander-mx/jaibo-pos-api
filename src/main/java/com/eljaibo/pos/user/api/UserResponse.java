package com.eljaibo.pos.user.api;

import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        boolean active
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.isActive());
    }
}
