package com.eljaibo.pos.cash.api;

import com.eljaibo.pos.cash.application.CashSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash-sessions")
public class CashSessionController {

    private final CashSessionService cashSessionService;

    public CashSessionController(
            CashSessionService cashSessionService
    ) {
        this.cashSessionService = cashSessionService;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionResponse open(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @Valid @RequestBody OpenCashSessionRequest request
    ) {
        return cashSessionService.open(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("user_id"))),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                terminalId,
                request
        );
    }

    @GetMapping("/current")
    public ResponseEntity<CashSessionResponse> current(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId
    ) {
        return cashSessionService
                .findCurrent(
                        UUID.fromString(
                                Objects.requireNonNull(jwt.getClaimAsString("business_id"))
                        ),
                        terminalId
                )
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.noContent().build()
                );
    }

    @PostMapping("/{cashSessionId}/close")
    public CashSessionResponse close(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID cashSessionId,
            @Valid @RequestBody CloseCashSessionRequest request
    ) {
        return cashSessionService.close(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("user_id"))),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                terminalId,
                cashSessionId,
                request
        );
    }

    @PostMapping("/{cashSessionId}/reopen")
    public CashSessionResponse reopen(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID cashSessionId,
            @Valid @RequestBody ReopenCashSessionRequest request
    ) {
        return cashSessionService.reopen(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("user_id"))),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                terminalId,
                cashSessionId,
                request
        );
    }

    @GetMapping("/{cashSessionId}/movement-summary")
    public CashMovementSummaryResponse movementSummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId,
            @PathVariable UUID cashSessionId
    ) {
        return cashSessionService.findMovementSummary(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId,
                cashSessionId
        );
    }

    @GetMapping("/latest")
    public ResponseEntity<CashSessionResponse> latest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId
    ) {
        return cashSessionService
                .findLatest(
                        UUID.fromString(
                                Objects.requireNonNull(
                                        jwt.getClaimAsString("business_id")
                                )
                        ),
                        terminalId
                )
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.noContent().build()
                );
    }
}