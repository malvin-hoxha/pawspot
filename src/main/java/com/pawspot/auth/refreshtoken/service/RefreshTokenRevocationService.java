package com.pawspot.auth.refreshtoken.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;


//Revokes all active refresh tokens belonging to a token family
@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(
        RefreshTokenRepository refreshTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
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