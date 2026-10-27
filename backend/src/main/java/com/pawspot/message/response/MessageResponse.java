package com.pawspot.message.response;

import java.time.Instant;

public record MessageResponse(
    Long id,
    Long senderId,
    String senderName,
    String body,
    Instant createdAt
) {}
