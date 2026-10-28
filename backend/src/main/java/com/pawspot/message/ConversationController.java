package com.pawspot.message;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.common.PageResponse;
import com.pawspot.message.mapper.ConversationMapper;
import com.pawspot.message.request.SendMessageRequest;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.response.MessageResponse;
import com.pawspot.message.service.ConversationService;
import com.pawspot.user.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;

    public ConversationController(
        ConversationService conversationService,
        ConversationMapper conversationMapper
    ) {
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ConversationResponse>> getConversations(
        Authentication authentication,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        User user = (User) authentication.getPrincipal();
        Page<ConversationResponse> convercations = conversationService
            .getConversations(user.getId(), page, size);

        return ResponseEntity.ok(
            conversationMapper.toConversationPageResponse(convercations)
        );

    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
        @PathVariable Long conversationId,
        @Valid @RequestBody SendMessageRequest request,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        MessageResponse messageResponse = 
            conversationService.sendMessage(conversationId, user, request.body());

        return ResponseEntity.status(HttpStatus.CREATED).body(messageResponse);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
        @PathVariable Long conversationId,
        Authentication authentication
    ) {

        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
            conversationService.getConversation(conversationId, user.getId())
        );
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<PageResponse<MessageResponse>> getMessages(
        @PathVariable Long conversationId,
        Authentication authentication,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        User user = (User) authentication.getPrincipal();
        Page<MessageResponse> messages = 
            conversationService.getMessages(conversationId, user.getId(), page, size);

        return ResponseEntity.ok(conversationMapper.toPageResponse(messages));
    }

    

    
    
}
