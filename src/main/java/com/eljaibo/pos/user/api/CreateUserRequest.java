package com.eljaibo.pos.user.api;

import com.eljaibo.pos.user.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record CreateUserRequest(
        @NotBlank @Size(max = 60) String username,
        @ValidPassword String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotNull UserRole role
) { }
