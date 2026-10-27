package com.pawspot.message.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pawspot.message.Conversation;
import com.pawspot.message.mapper.ConversationMapper;
import com.pawspot.message.repositories.ConversationRepository;
import com.pawspot.message.repositories.MessageRepository;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.response.MessageResponse;
import com.pawspot.user.User;

@Service 
public class ConversationService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationMapper conversationMapper;

     public ConversationService(
        ConversationRepository conversations,
        MessageRepository messages,
        ConversationMapper conversationMapper
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.conversationMapper = conversationMapper;
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long conversationId, Long userId) {
        Conversation conversation = requireParticipant(conversationId, userId);
        User postOwner = conversation.getPost().getUser();
        User interestedUser = conversation.getInterestedUser();

        User otherUser = userId.equals(postOwner.getId())
            ? interestedUser
            : postOwner;
        ConversationResponse response = conversationMapper.toResponse(conversation, otherUser);
        return response;
        
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long conversationId, Long userId, int page, int size) {
        requireParticipant(conversationId, userId);

        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return messages.findByConversationId(conversationId, pageable)
            .map(message -> new MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getBody(),
                message.getCreatedAt()
            ));

    }

    private Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(this::notFound);

        Long interestedUserId = conversation.getInterestedUser().getId();
        Long postOwnerId = conversation.getPost().getUser().getId();

        if(!userId.equals(postOwnerId) && !userId.equals(interestedUserId)) {
            // Do not reveal whether another user's conversation exists.
            throw notFound();
        }

        return conversation;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Conversation not found"
        );
    }


}
