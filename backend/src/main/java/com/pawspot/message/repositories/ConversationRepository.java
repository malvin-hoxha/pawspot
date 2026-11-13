package com.pawspot.message.repositories;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawspot.message.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long>{
    Optional<Conversation> findByPostIdAndInterestedUserId(
        Long postId,
        Long interestedUserId
    );

    // Include conversations where the user is either the interested person or the post owner.
    @Query("""
        select c from Conversation c
        where c.interestedUser.id = :userId
            or c.post.user.id = :userId
    """)
    Page<Conversation> findInbox(@Param("userId") Long userId, Pageable pageable);
}
