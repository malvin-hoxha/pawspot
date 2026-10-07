package com.pawspot.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

public interface PetPostRepository extends JpaRepository<PetPost, Long>{
    List<PetPost> findByUserId(Long userId);

    List<PetPost> findByType(PostType type);

    List<PetPost> findByStatus(PostStatus status);
}
