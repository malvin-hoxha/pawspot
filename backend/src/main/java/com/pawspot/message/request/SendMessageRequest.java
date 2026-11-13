package com.pawspot.message.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank 
    @Size(max = 2000)
    String body
) {}
