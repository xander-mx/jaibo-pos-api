package com.eljaibo.pos.user.domain;

import java.util.UUID;

public record UserCredentialsChanged(UUID actorId, UUID businessId, UUID userId, long credentialVersion) { }
