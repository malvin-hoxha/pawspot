package com.pawspot.auth;

public class AuthResponse {
    private String accessToken;
    private UserResponse user;

    public AuthResponse(String accessToken, UserResponse user) {
        this.accessToken = accessToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public UserResponse getUser() {
        return user;
    }
}
