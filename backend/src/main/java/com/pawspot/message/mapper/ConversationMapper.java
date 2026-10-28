package com.pawspot.message.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import com.pawspot.common.PageResponse;
import com.pawspot.message.Conversation;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.response.MessageResponse;
import com.pawspot.user.User;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(source = "conversation.id" ,target = "id")
    @Mapping(source = "conversation.post.id", target = "postId")
    @Mapping(source = "conversation.post.title", target = "postTitle")
    @Mapping(source = "otherUser.id", target = "otherUserId")
    @Mapping(source = "otherUser.name", target = "otherUserName")
    @Mapping(source = "conversation.createdAt", target = "createdAt")
    ConversationResponse toResponse(Conversation conversation, User otherUser);

    @Mapping(source = "number", target = "page")
    PageResponse<MessageResponse> toPageResponse(Page<MessageResponse> messages);
}

