package com.eljaibo.pos.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Missing claims are compatible only with accounts whose credentials have never changed. */
public final class CredentialVersion {
    private CredentialVersion() { }

    public static long from(Jwt jwt) {
        Object value = jwt.getClaims().get("credential_version");
        if (value == null) return 0;
        if (value instanceof Long version) return version >= 0 ? version : -1;
        if (value instanceof Integer version) return version >= 0 ? version.longValue() : -1;
        return -1;
    }
}
