package com.eljaibo.pos.terminal.api;

import com.eljaibo.pos.terminal.application.TerminalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalResponse register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterTerminalRequest request
    ) {
        return terminalService.register(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("user_id"))),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                request
        );
    }

    @GetMapping
    public List<TerminalResponse> findActive(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return terminalService.findActive(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id")))
        );
    }

    @GetMapping("/current")
    public CurrentTerminalResponse current(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id") UUID terminalId
    ) {
        return terminalService.current(
                UUID.fromString(
                        Objects.requireNonNull(
                                jwt.getClaimAsString("business_id")
                        )
                ),
                terminalId
        );
    }
}