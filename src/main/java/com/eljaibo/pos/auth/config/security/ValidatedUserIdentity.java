package com.eljaibo.pos.auth.security;

import com.eljaibo.pos.user.domain.UserRole;
import java.util.UUID;

/** Immutable identity valid only for the current HTTP request, never cached across requests. */
public record ValidatedUserIdentity(UUID userId, UUID businessId, UserRole role, Object requestDetails) {
}
