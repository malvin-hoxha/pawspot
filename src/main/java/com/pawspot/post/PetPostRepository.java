package com.pawspot.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PetPostRepository extends JpaRepository<PetPost, Long>{
    List<PetPost> findByUserId(Long userId);

    Page<PetPost> findByType(PostType type, Pageable pageable);

    List<PetPost> findByStatus(PostStatus status);
}
