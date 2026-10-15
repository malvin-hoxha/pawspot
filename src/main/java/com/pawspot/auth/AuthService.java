package com.pawspot.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.service.RefreshTokenRevocationService;
import com.pawspot.auth.refreshtoken.service.RefreshTokenService;
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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

//User registration, credential validation, and login
@Service 
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenRepository refreshTokenRepository,
        RefreshTokenRevocationService refreshTokenRevocationService, RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
        this.refreshTokenService = refreshTokenService;
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

        GeneratedRefreshToken generatedRefreshToken = refreshTokenService.createSession(user);

        String accessToken = jwtService.generateAccessToken(user);

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
            Claims claims = jwtService.validateRefreshToken(refreshToken);

            String email = claims.getSubject();
            String tokenId = claims.getId();
            String familyId = claims.get("family_id", String.class);

            User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                    new InvalidCredentialsException("Invalid refresh token")
            );

            RefreshToken storedToken = refreshTokenRepository
                .findByTokenIdForUpdate(tokenId)
                .orElseThrow(() ->
                    new InvalidCredentialsException(
                        "Invalid refresh token"
                    )
            );
            
            //REUSE DETECTION
            if (storedToken.isRevoked()) {

                refreshTokenRevocationService
                    .revokeFamily(
                        storedToken.getFamilyId()
                    );

                throw new InvalidCredentialsException(
                    "Refresh token reuse detected"
                );
            }

            if (
                !storedToken.getUser().getId().equals(user.getId()) ||
                !storedToken.getFamilyId().equals(familyId)
            ) {
                throw new InvalidCredentialsException(
                    "Invalid refresh token"
                );
            }

            storedToken.setRevokedAt(Instant.now());
            String newAccessToken = jwtService.generateAccessToken(user);

            GeneratedRefreshToken newRefreshToken = jwtService.generateRefreshToken(
                user,
                storedToken.getFamilyId()
            );

            RefreshToken newRefreshTokenEntity = new RefreshToken();

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
}
