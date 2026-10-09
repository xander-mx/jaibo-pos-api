package com.eljaibo.pos.catalog.api;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        int displayOrder,
        boolean active
) {
}