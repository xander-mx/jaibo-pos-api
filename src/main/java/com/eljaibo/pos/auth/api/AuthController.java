package com.eljaibo.pos.auth.api;

import com.eljaibo.pos.auth.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        try {
            return authService.login(request);
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password"
            );
        }
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new CurrentUserResponse(
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("user_id"))),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("business_id"))),
                jwt.getSubject(),
                jwt.getClaimAsString("display_name"),
                jwt.getClaimAsStringList("roles")
        );
    }
}