package com.pawspot.post.mapper;

import com.pawspot.post.PetPost;
import com.pawspot.post.response.PetPostResponse;

public class PetPostMapper {

    public static PetPostResponse toResponse(PetPost post) {
        return new PetPostResponse(
            post.getId(),
            post.getType(),
            post.getTitle(),
            post.getDescription(),
            post.getSpecies(),
            post.getCity(),
            post.getArea(),
            post.getStatus(),
            post.getImageUrl(),
            post.getCreatedAt(),
            post.getUpdatedAt(),
            post.getUser().getId(),
            post.getUser().getName()
        );
    }
}
