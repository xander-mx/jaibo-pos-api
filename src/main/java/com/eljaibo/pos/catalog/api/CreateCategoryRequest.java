package com.eljaibo.pos.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank
        @Size(max = 80)
        String name,

        @PositiveOrZero
        Integer displayOrder
) {
}