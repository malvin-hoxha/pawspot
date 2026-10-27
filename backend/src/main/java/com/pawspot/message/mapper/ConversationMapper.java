package com.pawspot.message.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.pawspot.message.Conversation;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.user.User;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(source = "conversation.id" ,target = "id")
    @Mapping(source = "conversation.post.id", target = "postId")
    @Mapping(source = "conversation.post.title", target = "postTitle")
    @Mapping(source = "otherUser.id", target = "otherUserId")
    @Mapping(source = "otherUser.name", target = "otherUserName")
    @Mapping(source = "conversation.createdAt", target = "createdAt")
    ConversationResponse toResponse (Conversation conversation, User otherUser);
}

