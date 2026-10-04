package com.pawspot.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PetPostRepository extends JpaRepository<PetPost, Long>{
    List<PetPost> findByUserId(Long userId);

    List<PetPost> findByType(PostType type);

    List<PetPost> findByStatus(PostStatus status);
}
