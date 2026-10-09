package com.eljaibo.pos.terminal.api;

import java.util.UUID;

public record CurrentTerminalResponse(
        UUID terminalId,
        String terminalName,
        UUID branchId,
        String branchName
) {
}