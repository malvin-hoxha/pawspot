package com.pawspot.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

//Creates and clears authentication cookies
@Component
public class AuthCookieFactory {

    private final long accessExpiration;
    private final long refreshExpiration;
    private final boolean secure;

    public AuthCookieFactory(
        @Value("${jwt.access-expiration}") long accessExpiration,
        @Value("${jwt.refresh-expiration}") long refreshExpiration,
        @Value("${auth.cookie.secure:false}") boolean secure
    ) {
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.secure = secure;
    }

    public ResponseCookie createAccessCookie(String token) {
        return buildCookie(
            "accessToken",
            token,
            "/",
            Duration.ofMillis(accessExpiration)
        );
    }

    public ResponseCookie createRefreshCookie(String token) {
        return buildCookie(
            "refreshToken",
            token,
            "/api/auth",
            Duration.ofMillis(refreshExpiration)
        );
    }

    public ResponseCookie clearAccessCookie() {
        return buildCookie(
            "accessToken",
            "",
            "/",
            Duration.ZERO
        );
    }

    public ResponseCookie clearRefreshCookie() {
        return buildCookie(
            "refreshToken",
            "",
            "/api/auth",
            Duration.ZERO
        );
    }

    private ResponseCookie buildCookie(
        String name,
        String value,
        String path,
        Duration maxAge
    ) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();
    }
}