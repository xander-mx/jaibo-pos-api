package com.eljaibo.pos.catalog.api;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        int decimalPlaces
) {
}