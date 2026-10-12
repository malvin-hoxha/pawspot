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
    };

    public String generateAccessToken(User user) {
        return generateToken(
            user.getEmail(),
            accessExpiration,
            "access"
        );
    }

    public GeneratedRefreshToken generateRefreshToken(User user) {
        String familyId = UUID.randomUUID().toString();

        return generateRefreshToken(user, familyId);
    }

    private String generateToken(
        String email,
        long expiration,
        String tokenType
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .subject(email)
            .claim("token_type", tokenType)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(signingKey)
            .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isAccessTokenValid(String token, User user) {
        return isTokenValid(token, user, "access");
    }

    public boolean isRefreshTokenValid(String token, User user) {
        return isTokenValid(token, user, "refresh");
    }

    private boolean isTokenValid(
        String token,
        User user,
        String expectedType
    ) {
        String email = extractEmail(token);
        String tokenType = extractTokenType(token);

        return email.equals(user.getEmail())
            && expectedType.equals(tokenType)
            && !isTokenExpired(token);
    }

    public String extractTokenType(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("token_type", String.class);
    }

    public GeneratedRefreshToken generateRefreshToken(
        User user,
        String familyId
    ) {
        String tokenId = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant expiresAt =
            now.plusMillis(refreshExpiration);

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

    public String extractTokenId(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getId();
    }

    public String extractFamilyId(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("family_id", String.class);
    }
}
