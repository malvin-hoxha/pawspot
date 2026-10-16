package com.pawspot.user.request;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
    @NotBlank String password
) {}
