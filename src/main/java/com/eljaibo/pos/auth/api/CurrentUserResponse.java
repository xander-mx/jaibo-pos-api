package com.eljaibo.pos.auth.api;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        UUID businessId,
        String username,
        String displayName,
        List<String> roles
) {
}