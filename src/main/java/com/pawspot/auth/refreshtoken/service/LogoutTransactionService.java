package com.pawspot.auth.refreshtoken.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.entity.RefreshSession;
import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;

//Performs server-side logout within a database transaction
@Service
public class LogoutTransactionService {

    private final RefreshSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final RefreshTokenRevocationService revocationService;

    public LogoutTransactionService(
        RefreshSessionRepository sessionRepository,
        RefreshTokenRepository tokenRepository,
        RefreshTokenRevocationService revocationService
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.revocationService = revocationService;
    }

    @Transactional
    public void execute(
        String tokenId,
        String familyId,
        String email
    ) {
        // 1. Κλειδώνουμε πρώτα τη session
        RefreshSession session = sessionRepository
            .findByIdForUpdate(familyId)
            .orElse(null);

        if (session == null || session.isRevoked()) {
            return;
        }

        // 2. Ελέγχουμε σε ποιον ανήκει
        if (!session.getUser().getEmail().equals(email)) {
            return;
        }

        // 3. Βρίσκουμε το συγκεκριμένο refresh token
        RefreshToken storedToken = tokenRepository
            .findByTokenId(tokenId)
            .orElse(null);

        if (storedToken == null ||
            !storedToken.getFamilyId().equals(session.getId()) ||
            !storedToken.getUser().getId().equals(session.getUser().getId())) {
            return;
        }

        // 4. Ανακαλούμε τη session
        session.setRevokedAt(Instant.now());

        // 5. Ανακαλούμε τα ενεργά tokens της family
        revocationService.revokeFamily(session.getId());
    }
}