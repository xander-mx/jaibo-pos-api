package com.eljaibo.pos.auth.application;

import com.eljaibo.pos.auth.config.JwtProperties;
import com.eljaibo.pos.auth.security.AuthenticatedUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(AuthenticatedUser user, long credentialVersion) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("user_id", user.getId().toString())
                .claim("business_id", user.getBusinessId().toString())
                .claim("display_name", user.getDisplayName())
                .claim("roles", List.of(user.getRole().name()))
                .claim("credential_version", credentialVersion)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String value = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new IssuedToken(value, issuedAt, expiresAt);
    }

    public record IssuedToken(
            String value,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
