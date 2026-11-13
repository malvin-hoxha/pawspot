package com.pawspot.message.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pawspot.message.Message;

public interface MessageRepository extends JpaRepository<Message, Long>{
    // Load the sender with each message so the response can include their name.
    @EntityGraph(attributePaths = "sender")
    Page<Message> findByConversationId(Long conversationId, Pageable pageable);
}
