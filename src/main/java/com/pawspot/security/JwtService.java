package com.pawspot.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pawspot.user.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

//Generates and validates JWTs
@Service 
public class JwtService {
    private final SecretKey signingKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiration}") long accessExpiration,
        @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        return Jwts.builder()
            .subject(user.getEmail())
            .claim("token_type", "access")
            .issuedAt(Date.from(now))
            .expiration(
                Date.from(now.plusMillis(accessExpiration))
            )
            .signWith(signingKey)
            .compact();
    }

    public GeneratedRefreshToken generateRefreshToken(
        User user,
        String familyId
    ) {
        Instant expiresAt = Instant.now().plusMillis(refreshExpiration);

        return generateRefreshToken(user, familyId, expiresAt);
    }

    public GeneratedRefreshToken generateRefreshToken(
        User user,
        String familyId,
        Instant expiresAt
    ) {
        String tokenId = UUID.randomUUID().toString();

        Instant now = Instant.now();

        String token = Jwts.builder()
            .subject(user.getEmail())
            .id(tokenId)
            .claim("token_type", "refresh")
            .claim("family_id", familyId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

        return new GeneratedRefreshToken(
            token,
            tokenId,
            familyId,
            expiresAt
        );
    }

    public Claims validateAccessToken(String token) {
        return validateToken(token, "access");
    }

    public Claims validateRefreshToken(String token) {
        return validateToken(token, "refresh");
    }

    private Claims validateToken(
        String token,
        String expectedType
    ) {
        Claims claims = parseToken(token);

        String tokenType = claims.get(
            "token_type",
            String.class
        );

        if (!expectedType.equals(tokenType)) {
            throw new JwtException("Invalid token type");
        }

        if (claims.getSubject() == null ||
            claims.getSubject().isBlank()) {
            throw new JwtException("Missing token subject");
        }

        if (claims.getExpiration() == null) {
            throw new JwtException("Missing token expiration");
        }

        if ("refresh".equals(expectedType)) {
            String tokenId = claims.getId();
            String familyId = claims.get("family_id", String.class);

            if (tokenId == null || tokenId.isBlank() ||
                familyId == null || familyId.isBlank()) {
                throw new JwtException("Invalid refresh token claims");
            }
        }

        return claims;
    }
}
