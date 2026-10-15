package com.eljaibo.pos.auth.security;

import com.eljaibo.pos.user.persistence.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Checks account state once per protected HTTP request, after cryptographic JWT validation. */
public class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();

    public ActiveUserFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(path))
                || "/actuator/health".equals(path) || "/actuator/info".equals(path)
                || "/ws".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        if (context.getAuthentication() instanceof JwtAuthenticationToken authentication) {
            AppUserRepository.ActiveIdentity identity;
            try {
                UUID userId = UUID.fromString(authentication.getToken().getClaimAsString("user_id"));
                UUID businessId = UUID.fromString(authentication.getToken().getClaimAsString("business_id"));
                identity = users.findActiveIdentity(userId, businessId).orElse(null);
            } catch (IllegalArgumentException | NullPointerException exception) {
                identity = null;
            }
            if (identity == null || identity.getCredentialVersion() != CredentialVersion.from(authentication.getToken())) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response,
                        new InvalidBearerTokenException("Active user from this business required"));
                return;
            }
            // Keep Jwt as principal for existing controllers and use the current persisted role.
            JwtAuthenticationToken validated = new JwtAuthenticationToken(authentication.getToken(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + identity.getRole().name())),
                    authentication.getName());
            validated.setDetails(new ValidatedUserIdentity(identity.getId(), identity.getBusinessId(),
                    identity.getRole(), authentication.getDetails()));
            context.setAuthentication(validated);
        }
        chain.doFilter(request, response);
    }
}
