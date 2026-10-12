package com.pawspot.security;

import java.time.Instant;

public record GeneratedRefreshToken(
    String token,
    String tokenId,
    String familyId,
    Instant expiresAt
) {}
