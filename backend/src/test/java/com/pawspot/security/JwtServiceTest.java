package com.pawspot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pawspot.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

public class JwtServiceTest {
    
    private JwtService jwtService;
    private User user;

    @BeforeEach 
    void setUp() {
        String secret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        jwtService = new JwtService(secret, 900_000L, 604_800_000L);

        user = new User();
        user.setEmail("test@pawspot.com");
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.validateAccessToken(token);

        assertEquals("test@pawspot.com", claims.getSubject());
        assertEquals("access", claims.get("token_type", String.class));
        assertNotNull(claims.getExpiration());
    }

    @Test
    void shouldRejectRefreshTokenAsAccessToken() {
        GeneratedRefreshToken refreshToken = jwtService.generateRefreshToken(user, "test-family");

        assertThrows(JwtException.class,
            () -> jwtService.validateAccessToken(refreshToken.token())
        );
    }

    @Test
    void shouldRejectExpiredAccessToken() throws InterruptedException {
        JwtService shortLivedJwtService = new JwtService(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            -60_000L,
            604_800_000L
        );

        String token = shortLivedJwtService.generateAccessToken(user);

        assertThrows(JwtException.class,
            () -> shortLivedJwtService.validateAccessToken(token)
        );
    }


}
