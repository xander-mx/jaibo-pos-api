package com.eljaibo.pos.terminal.api;

import java.time.Instant;
import java.util.UUID;

public record TerminalResponse(
        UUID id,
        UUID branchId,
        String name,
        boolean active,
        UUID registeredByUserId,
        String registeredByDisplayName,
        Instant lastSeenAt,
        Instant createdAt
) {
}