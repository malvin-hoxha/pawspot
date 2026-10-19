package com.pawspot.auth.refreshtoken.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;

@Service
public class RefreshTokenCleanupService {
    private final RefreshTokenRepository tokenRepository;
    private final RefreshSessionRepository sessionRepository;

    public RefreshTokenCleanupService(
        RefreshTokenRepository tokenRepository,
        RefreshSessionRepository sessionRepository
    ) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();

        tokenRepository.deleteExpiredTokens(now);
        sessionRepository.deleteExpiredSessions(now);
    }
}
