package com.pawspot.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.auth.request.LoginRequest;
import com.pawspot.auth.request.RegisterRequest;
import com.pawspot.auth.response.AuthResponse;
import com.pawspot.auth.response.LoginResult;
import com.pawspot.auth.response.RefreshResult;
import com.pawspot.auth.response.UserResponse;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.user.User;

import java.time.Duration;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final long accessExpiration;
    private final long refreshExpiration;

    public AuthController(
        AuthService authService,
        @Value("${jwt.access-expiration}") long accessExpiration,
        @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.authService = authService;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);

        UserResponse response = new UserResponse(user.getId(), user.getName(),user.getEmail(), user.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        LoginResult result = authService.login(request);
        ResponseCookie accessCookie = ResponseCookie
            .from("accessToken", result.accessToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofMillis(accessExpiration))
            .build();

        ResponseCookie refreshCookie = ResponseCookie
            .from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ofMillis(refreshExpiration))
            .build();

        AuthResponse response = new AuthResponse(result.user());

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
            )
            .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        UserResponse response = new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh (@CookieValue(
        name = "refreshToken",
        required = false
    ) String refreshToken) 
    {
        if (refreshToken == null) {
            throw new InvalidCredentialsException(
                "Refresh token is missing"
            );
        }

        RefreshResult result = authService.refreshTokens(refreshToken);

        ResponseCookie accessCookie = ResponseCookie
            .from("accessToken", result.accessToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofMillis(accessExpiration))
            .build();


        ResponseCookie refreshCookie = ResponseCookie
            .from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ofMillis(refreshExpiration))
            .build();

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
            )
            .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(
            name = "refreshToken",
            required = false
        ) String refreshToken
    ) {
        authService.logout(refreshToken);

        ResponseCookie accessCookie = ResponseCookie
            .from("accessToken", "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();

        ResponseCookie refreshCookie = ResponseCookie
            .from("refreshToken", "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();

        return ResponseEntity.noContent()
            .header(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
            )
            .build();
    }
}
