package com.eljaibo.pos.cash.api;

import jakarta.validation.constraints.Size;

public record ReopenCashSessionRequest(
        @Size(max = 255)
        String comment
) {
}