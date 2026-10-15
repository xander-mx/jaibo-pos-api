package com.eljaibo.pos.user.api;

import com.eljaibo.pos.user.application.UserService;
import com.eljaibo.pos.auth.security.CredentialVersion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me/password")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(claim(jwt, "user_id"), claim(jwt, "business_id"),
                CredentialVersion.from(jwt), request);
    }

    @PatchMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(claim(jwt, "user_id"), claim(jwt, "business_id"),
                CredentialVersion.from(jwt), userId, request);
    }

    @GetMapping
    public List<UserResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return userService.findAll(claim(jwt, "user_id"), claim(jwt, "business_id"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.create(claim(jwt, "user_id"), claim(jwt, "business_id"), request);
    }

    private UUID claim(Jwt jwt, String name) {
        return UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString(name)));
    }

    @PatchMapping("/{userId}/activate")
    public UserResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return userService.activate(claim(jwt, "user_id"), claim(jwt, "business_id"), userId);
    }

    @PatchMapping("/{userId}/deactivate")
    public UserResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return userService.deactivate(claim(jwt, "user_id"), claim(jwt, "business_id"), userId);
    }
}
