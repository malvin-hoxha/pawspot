package com.pawspot.user.request;

import com.pawspot.validation.ValidBcryptPassword;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
    @NotBlank 
    @ValidBcryptPassword
    String password
) {}
