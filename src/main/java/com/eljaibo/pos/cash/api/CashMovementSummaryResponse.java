package com.eljaibo.pos.cash.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CashMovementSummaryResponse(
        UUID cashSessionId,
        BigDecimal openingAmount,
        BigDecimal incomeTotal,
        BigDecimal outcomeTotal,
        BigDecimal expectedAmount,
        List<CashMovementTypeSummaryResponse> movements
) {
}