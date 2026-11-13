package com.pawspot.message;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.service.ConversationService;
import com.pawspot.user.User;

@RestController
@RequestMapping("/api/posts")
public class PostConversationController {
    private final ConversationService conversationService;

    public PostConversationController(
        ConversationService conversationService
    ){
        this.conversationService = conversationService;
    }

    @PostMapping("/{postId}/conversations")
    public ResponseEntity<ConversationResponse> startConversation(
        @PathVariable Long postId,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
            conversationService.startConversation(postId, user)
        );
    }
}
