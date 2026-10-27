package com.pawspot.message.response;

import java.time.Instant;

public record ConversationResponse (
    Long id,
    Long postId,
    String postTitle,
    Long otherUserId,
    String otherUserName,
    Instant createdAt
){}
