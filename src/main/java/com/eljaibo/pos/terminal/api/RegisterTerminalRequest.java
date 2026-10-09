package com.eljaibo.pos.terminal.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterTerminalRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        UUID branchId
) {
}