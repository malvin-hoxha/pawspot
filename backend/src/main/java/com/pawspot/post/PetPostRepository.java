package com.pawspot.post;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import jakarta.persistence.LockModeType;

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

    Page<PetPost> findByUserId(Long userId, Pageable pageable);

    @Query("""
        select p from PetPost p
        where p.id = :id
    """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PetPost> findByIdForUpdate(@Param("id") Long id);
}
