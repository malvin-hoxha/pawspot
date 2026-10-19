package com.pawspot.user;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.auth.AuthCookieFactory;
import com.pawspot.user.request.DeleteAccountRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final AuthCookieFactory authCookieFactory;

    public UserController(UserService userService, AuthCookieFactory authCookieFactory) {
        this.userService = userService;
        this.authCookieFactory = authCookieFactory;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
        @Valid @RequestBody DeleteAccountRequest request,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        userService.deleteAccount(user.getId(), request.password());



        return ResponseEntity.noContent()
            .header(
                HttpHeaders.SET_COOKIE,
                authCookieFactory.clearAccessCookie().toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                authCookieFactory.clearRefreshCookie().toString()
            )
            .build();
    }
}
