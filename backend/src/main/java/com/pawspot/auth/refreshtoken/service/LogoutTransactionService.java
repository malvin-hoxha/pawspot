package com.pawspot.auth.refreshtoken.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.entity.RefreshSession;
import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

//Performs server-side logout within a database transaction
@Service
public class LogoutTransactionService {

    private final RefreshSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final RefreshTokenRevocationService revocationService;
    private final UserRepository userRepository;

    public LogoutTransactionService(
        RefreshSessionRepository sessionRepository,
        RefreshTokenRepository tokenRepository,
        RefreshTokenRevocationService revocationService,
        UserRepository userRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.revocationService = revocationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public void execute(
        String tokenId,
        String familyId,
        String email
    ) {


        User user = userRepository.findByEmail(email)
            .orElse(null);

        if (user == null) {
            return;
        }

        User lockedUser = userRepository.findByIdForUpdate(user.getId())
            .orElse(null);

        if (lockedUser == null) {
            return;
        }
        // 1. Κλειδώνουμε πρώτα τη session
        RefreshSession session = sessionRepository
            .findByIdForUpdate(familyId)
            .orElse(null);

        if (session == null || session.isRevoked()) {
            return;
        }

        // 2. Ελέγχουμε σε ποιον ανήκει
        if (!session.getUser().getId().equals(lockedUser.getId())) {
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