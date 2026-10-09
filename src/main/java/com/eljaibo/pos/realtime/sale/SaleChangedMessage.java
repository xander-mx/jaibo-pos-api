package com.eljaibo.pos.realtime.sale;

import java.time.Instant;
import java.util.UUID;

public record SaleChangedMessage(
        UUID saleId,
        UUID branchId,
        String type,
        Instant occurredAt
) {
}