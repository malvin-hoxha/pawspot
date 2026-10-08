package com.pawspot.post.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import com.pawspot.common.PageResponse;
import com.pawspot.post.PetPost;
import com.pawspot.post.request.CreatePetPostRequest;
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
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "imagePublicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updatePostFromRequest(
        UpdatePetPostRequest request,
        @MappingTarget PetPost post
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "imagePublicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    PetPost toEntity(CreatePetPostRequest request);

    default PageResponse<PetPostResponse> toPageResponse(
            Page<PetPost> posts
    ) {
        List<PetPostResponse> content = posts.getContent()
            .stream()
            .map(this::toResponse)
            .toList();

        return new PageResponse<>(
            content,
            posts.getNumber(),
            posts.getSize(),
            posts.getTotalElements(),
            posts.getTotalPages(),
            posts.isFirst(),
            posts.isLast()
        );
    }

    List<PetPostResponse> toResponseList(List<PetPost> posts);
}
