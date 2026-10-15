package com.pawspot.auth.refreshtoken.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

//Executes refresh token rotation within a database transaction
@Service
public class RotationTransactionService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService revocationService;
    private final RefreshSessionRepository refreshSessionRepository;

    public RotationTransactionService(
        JwtService jwtService,
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        RefreshTokenRevocationService revocationService,
        RefreshSessionRepository refreshSessionRepository
        
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationService = revocationService;
        this.refreshSessionRepository = refreshSessionRepository;
    }

    @Transactional
    public RotationOutcome execute(String refreshToken) {
        try {
            Claims claims = jwtService.validateRefreshToken(refreshToken);

            String email = claims.getSubject();
            String tokenId = claims.getId();
            String familyId = claims.get("family_id", String.class);

            User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                    new InvalidCredentialsException("Invalid refresh token")
                );

            RefreshSession session = refreshSessionRepository
                .findByIdForUpdate(familyId)
                .orElseThrow(() ->
                    new InvalidCredentialsException("Invalid refresh session")
                );

            if (session.isRevoked()) {
                throw new InvalidCredentialsException(
                    "Refresh session has been revoked"
                );
            }

            if (!session.getUser().getId().equals(user.getId())) {
                throw new InvalidCredentialsException(
                    "Invalid refresh session"
                );
            }

            RefreshToken storedToken = refreshTokenRepository
                .findByTokenId(tokenId)
                .orElseThrow(() ->
                    new InvalidCredentialsException("Invalid refresh token")
                );

            if (!storedToken.getUser().getId().equals(user.getId()) || !storedToken.getFamilyId().equals(familyId)) {
                throw new InvalidCredentialsException("Invalid refresh token");
            }

            // REUSE DETECTION
            if (storedToken.isRevoked()) {
                session.setRevokedAt(Instant.now());

                revocationService.revokeFamily(session.getId());

                return new RotationOutcome(null, true);
            }

            if (!storedToken.getExpiresAt().isAfter(Instant.now())) {
                throw new InvalidCredentialsException("Expired refresh token");
            }

            // Normal rotation
            storedToken.setRevokedAt(Instant.now());

            String newAccessToken = jwtService.generateAccessToken(user);

            GeneratedRefreshToken newRefreshToken = jwtService.generateRefreshToken(
                user,
                storedToken.getFamilyId(),
                storedToken.getExpiresAt()
            );

            refreshTokenRepository.save(RefreshToken.from(newRefreshToken, user));

            return new RotationOutcome(
                new RefreshResult(
                    newAccessToken,
                    newRefreshToken.token()
                ),
                false
            );

        } catch (JwtException e) {
            throw new InvalidCredentialsException(
                "Invalid or expired refresh token"
            );
        }
    }
}