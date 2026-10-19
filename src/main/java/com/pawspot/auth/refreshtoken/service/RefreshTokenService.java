package com.pawspot.auth.refreshtoken.service;

import org.springframework.stereotype.Service;

import com.pawspot.auth.refreshtoken.RotationOutcome;
import com.pawspot.auth.refreshtoken.entity.RefreshSession;
import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.response.RefreshResult;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.security.GeneratedRefreshToken;
import com.pawspot.security.JwtService;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;

//Creates sessions and coordinates token rotation and logout
@Service
public class RefreshTokenService {
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RotationTransactionService rotationTransactionService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final LogoutTransactionService logoutTransactionService;
    private final UserRepository userRepository;

    public RefreshTokenService(
        JwtService jwtService,
        RefreshTokenRepository refreshTokenRepository,
        RotationTransactionService rotationTransactionService,
        RefreshSessionRepository refreshSessionRepository,
        LogoutTransactionService logoutTransactionService,
        UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.rotationTransactionService = rotationTransactionService;
        this.refreshSessionRepository = refreshSessionRepository;
        this.logoutTransactionService = logoutTransactionService;
        this.userRepository = userRepository;
    }

    @Transactional
    public GeneratedRefreshToken createSession(User user) {

        User lockedUser = userRepository.findByIdForUpdate(user.getId())
            .orElseThrow(() ->
                new InvalidCredentialsException("Invalid credentials")
            );

        // 1. Δημιουργία νέας login session
        RefreshSession session = new RefreshSession(lockedUser, null);

        // 2. Δημιουργία refresh JWT με το ID της session
        GeneratedRefreshToken token = jwtService.generateRefreshToken(
            lockedUser,
            session.getId()
        );

        session.setExpiresAt(token.expiresAt());

        // 3. Αποθήκευση στη βάση
        refreshSessionRepository.save(session);

        // 4. Αποθήκευση refresh token
        refreshTokenRepository.save(
            RefreshToken.from(token, lockedUser)
        );

        return token;
    }

    public RefreshResult rotate(String refreshToken) {

        RotationOutcome outcome = rotationTransactionService.execute(refreshToken);

        if (outcome.reuseDetected()) {
            throw new InvalidCredentialsException(
                "Refresh token reuse detected"
            );
        }

        return outcome.result();
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtService.validateRefreshToken(refreshToken);

            logoutTransactionService.execute(
                claims.getId(),
                claims.get("family_id", String.class),
                claims.getSubject()
            );

        } catch (JwtException e) {
            // Invalid / expired refresh token.
            // Ο controller καθαρίζει τα cookies.
        }
    }
}