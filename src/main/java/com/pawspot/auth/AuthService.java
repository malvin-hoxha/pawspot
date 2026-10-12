package com.pawspot.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.RefreshToken;
import com.pawspot.auth.refreshtoken.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.RefreshTokenRevocationService;
import com.pawspot.auth.request.LoginRequest;
import com.pawspot.auth.request.RegisterRequest;
import com.pawspot.auth.response.LoginResult;
import com.pawspot.auth.response.RefreshResult;
import com.pawspot.auth.response.UserResponse;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.security.GeneratedRefreshToken;
import com.pawspot.security.JwtService;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import io.jsonwebtoken.JwtException;

@Service 
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenRepository refreshTokenRepository,
        RefreshTokenRevocationService refreshTokenRevocationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevocationService = new RefreshTokenRevocationService(refreshTokenRepository);
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(
            passwordEncoder.encode(request.getPassword())
        );

        return userRepository.save(user);
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) 
            throw new InvalidCredentialsException("Invalid email or password");

        String accessToken = jwtService.generateAccessToken(user);
        GeneratedRefreshToken generatedRefreshToken = jwtService.generateRefreshToken(user);

        RefreshToken refreshTokenEntity = new RefreshToken();

        refreshTokenEntity.setTokenId(
            generatedRefreshToken.tokenId()
        );

        refreshTokenEntity.setFamilyId(
            generatedRefreshToken.familyId()
        );

        refreshTokenEntity.setExpiresAt(
            generatedRefreshToken.expiresAt()
        );

        refreshTokenEntity.setUser(user);

        refreshTokenRepository.save(refreshTokenEntity);

        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getCreatedAt()
        );

        return new LoginResult(accessToken, generatedRefreshToken.token(), userResponse);
    }

    @Transactional
    public RefreshResult refreshTokens(String refreshToken) {

        try {
            String email = jwtService.extractEmail(refreshToken);

            String tokenId = jwtService.extractTokenId(refreshToken);

            User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                    new InvalidCredentialsException(
                        "Invalid refresh token"
                    )
                );

            if (!jwtService.isRefreshTokenValid(
                    refreshToken, user
            )) {
                throw new InvalidCredentialsException(
                    "Invalid refresh token"
                );
            }

            RefreshToken storedToken = refreshTokenRepository
                    .findByTokenIdForUpdate(tokenId)
                    .orElseThrow(() ->
                        new InvalidCredentialsException(
                            "Invalid refresh token"
                        )
                    );

            /*
            * REUSE DETECTION
            */
            if (storedToken.isRevoked()) {

                refreshTokenRevocationService
                    .revokeFamily(
                        storedToken.getFamilyId()
                    );

                throw new InvalidCredentialsException(
                    "Refresh token reuse detected"
                );
            }

            /*
            * Extra safety:
            * το token της DB πρέπει να ανήκει
            * στον ίδιο user.
            */
            if (!storedToken.getUser()
                    .getId()
                    .equals(user.getId())) {

                throw new InvalidCredentialsException(
                    "Invalid refresh token"
                );
            }

            /*
            * Normal rotation:
            * σκοτώνουμε το παλιό token.
            */
            storedToken.setRevokedAt(Instant.now());

            /*
            * Νέο access token.
            */
            String newAccessToken =
                jwtService.generateAccessToken(user);

            /*
            * Νέο refresh token,
            * ΙΔΙΑ family.
            */
            GeneratedRefreshToken newRefreshToken =
                jwtService.generateRefreshToken(
                    user,
                    storedToken.getFamilyId()
                );

            RefreshToken newRefreshTokenEntity =
                new RefreshToken();

            newRefreshTokenEntity.setTokenId(
                newRefreshToken.tokenId()
            );

            newRefreshTokenEntity.setFamilyId(
                newRefreshToken.familyId()
            );

            newRefreshTokenEntity.setExpiresAt(
                newRefreshToken.expiresAt()
            );

            newRefreshTokenEntity.setUser(user);

            refreshTokenRepository.save(
                newRefreshTokenEntity
            );

            return new RefreshResult(
                newAccessToken,
                newRefreshToken.token()
            );

        } catch (JwtException e) {
            throw new InvalidCredentialsException(
                "Invalid or expired refresh token"
            );
        }
    }

    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }

        try {
            String tokenId = jwtService.extractTokenId(refreshToken);

            refreshTokenRepository
                .findByTokenId(tokenId)
                .ifPresent(storedToken ->
                    refreshTokenRevocationService.revokeFamily(
                        storedToken.getFamilyId()
                    )
                );

        } catch (JwtException e) {
            // Δεν χρειάζεται να αποτύχει το logout.
            // Τα cookies θα διαγραφούν έτσι κι αλλιώς.
        }
    }
}
