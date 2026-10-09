package com.eljaibo.pos.auth.application;

import com.eljaibo.pos.auth.api.LoginRequest;
import com.eljaibo.pos.auth.api.LoginResponse;
import com.eljaibo.pos.auth.security.AuthenticatedUser;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository appUserRepository,
            JwtTokenService jwtTokenService
    ) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Hold the row lock from password verification through token issuance.
        appUserRepository.findForLogin(request.username());
        var authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(),
                        request.password()
                );

        var authentication = authenticationManager.authenticate(
                authenticationRequest
        );

        AuthenticatedUser principal =
                (AuthenticatedUser) authentication.getPrincipal();

        AppUser user = appUserRepository.findById(principal.getId())
                .orElseThrow();

        user.registerSuccessfulLogin();

        JwtTokenService.IssuedToken token =
                jwtTokenService.issue(principal, user.getCredentialVersion());

        long expiresInSeconds = Duration.between(
                token.issuedAt(),
                token.expiresAt()
        ).toSeconds();

        return new LoginResponse(
                token.value(),
                "Bearer",
                expiresInSeconds,
                token.expiresAt(),
                new LoginResponse.User(
                        principal.getId(),
                        principal.getBusinessId(),
                        principal.getUsername(),
                        principal.getDisplayName(),
                        principal.getRole()
                )
        );
    }
}
