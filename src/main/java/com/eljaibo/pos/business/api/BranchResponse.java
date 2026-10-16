package com.eljaibo.pos.business.api;

import java.util.UUID;

public record BranchResponse(
        UUID id,
        String code,
        String name
) {
}