package com.eljaibo.pos.cash.api;

import com.eljaibo.pos.cash.domain.CashSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashSessionResponse(
        UUID id,
        UUID operatingCycleId,
        LocalDate cycleStartsOn,
        LocalDate cycleEndsOn,
        LocalDate businessDate,
        CashSessionStatus status,
        BigDecimal openingAmount,
        Instant openedAt,
        UUID openedByUserId,
        String openedByDisplayName,
        UUID openedFromTerminalId,
        String openedFromTerminalName,
        BigDecimal closingExpectedAmount,
        BigDecimal closingCountedAmount,
        BigDecimal closingDifference,
        Instant closedAt,
        int reopenCount
) {
}