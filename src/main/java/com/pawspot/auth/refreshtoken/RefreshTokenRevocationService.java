package com.pawspot.auth.refreshtoken;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(
        RefreshTokenRepository refreshTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(String familyId) {

        List<RefreshToken> activeTokens =
            refreshTokenRepository
                .findAllByFamilyIdAndRevokedAtIsNull(familyId);

        Instant now = Instant.now();

        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
        }

        refreshTokenRepository.saveAll(activeTokens);
    }
}