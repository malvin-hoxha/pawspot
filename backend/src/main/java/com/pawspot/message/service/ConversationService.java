package com.pawspot.message.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pawspot.exception.ForbiddenOperationException;
import com.pawspot.exception.PostNotFoundException;
import com.pawspot.message.Conversation;
import com.pawspot.message.mapper.ConversationMapper;
import com.pawspot.message.repositories.ConversationRepository;
import com.pawspot.message.repositories.MessageRepository;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.response.MessageResponse;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.user.User;

@Service 
public class ConversationService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationMapper conversationMapper;
    private final PetPostRepository petPostRepository;

    public ConversationService(
        ConversationRepository conversations,
        MessageRepository messages,
        ConversationMapper conversationMapper,
        PetPostRepository petPostRepository
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.conversationMapper = conversationMapper;
        this.petPostRepository = petPostRepository;
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

    @Transactional
    public ConversationResponse startConversation(Long postId, User currentUser) {
        // Serialize conversation creation for the same post until the transaction completes.
        PetPost post = petPostRepository.findByIdForUpdate(postId).orElseThrow(
            () -> new PostNotFoundException("Post not found")
        );

        if (currentUser.getId().equals(post.getUser().getId())){
            throw new ForbiddenOperationException("Unable to message yourself");
        }

        Optional<Conversation> conversation = conversations.findByPostIdAndInterestedUserId(postId, currentUser.getId());

        if(conversation.isPresent()) {
            return conversationMapper.toResponse(conversation.get(), post.getUser());
        }

        if(post.getStatus()!= PostStatus.ACTIVE) {
            throw new ForbiddenOperationException("This post is no longer active");
        }

        Conversation newConversation = new Conversation();
        newConversation.setPost(post);
        newConversation.setInterestedUser(currentUser);

        conversations.save(newConversation);

        return conversationMapper.toResponse(newConversation, post.getUser());
        
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
