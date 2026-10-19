package com.pawspot.post;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PetPostRepository
    extends JpaRepository<PetPost, Long>, JpaSpecificationExecutor<PetPost> {
    List<PetPost> findByUserId(Long userId);

    Page<PetPost> findByType(PostType type, Pageable pageable);

    List<PetPost> findByStatus(PostStatus status);

    @EntityGraph(attributePaths = "user")
    Page<PetPost> findAll(
        Specification<PetPost> spec,
        Pageable pageable
    );
}
