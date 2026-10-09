package com.eljaibo.pos.cash.api;

import com.eljaibo.pos.cash.domain.CashMovementDirection;
import com.eljaibo.pos.cash.domain.CashMovementType;

import java.math.BigDecimal;

public record CashMovementTypeSummaryResponse(
        CashMovementType type,
        CashMovementDirection direction,
        BigDecimal total
) {
}