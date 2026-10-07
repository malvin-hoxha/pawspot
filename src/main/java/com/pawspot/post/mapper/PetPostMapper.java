package com.pawspot.post.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pawspot.post.PetPost;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.post.response.PetPostResponse;

@Mapper(componentModel = "spring")
public interface PetPostMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.name", target = "userName")
    PetPostResponse toResponse(PetPost post);

    @BeanMapping(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    void updatePostFromRequest(
        UpdatePetPostRequest request,
        @MappingTarget PetPost post
    );
}
