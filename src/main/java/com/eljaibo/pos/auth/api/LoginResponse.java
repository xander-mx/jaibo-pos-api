package com.eljaibo.pos.auth.api;

import com.eljaibo.pos.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        User user
) {

    public record User(
            UUID id,
            UUID businessId,
            String username,
            String displayName,
            UserRole role
    ) {
    }
}