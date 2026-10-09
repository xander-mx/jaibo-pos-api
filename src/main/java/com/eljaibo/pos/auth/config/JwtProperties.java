package com.eljaibo.pos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String secret
) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }

        if (accessTokenTtl == null
                || accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT access token TTL must be positive"
            );
        }

        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 bytes"
            );
        }
    }
}