package com.pawspot.auth.response;

public record LoginResult(
    String accessToken,
    String refreshToken,
    UserResponse user
) {}