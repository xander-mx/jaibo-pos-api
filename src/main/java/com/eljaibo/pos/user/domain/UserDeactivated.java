package com.eljaibo.pos.user.domain;

import java.util.UUID;

public record UserDeactivated(UUID administratorId, UUID businessId, UUID userId) {
}
