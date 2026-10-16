package com.pawspot.auth.refreshtoken.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawspot.auth.refreshtoken.entity.RefreshSession;

import jakarta.persistence.LockModeType;

public interface RefreshSessionRepository
        extends JpaRepository<RefreshSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM RefreshSession s
        WHERE s.id = :sessionId
    """)
    Optional<RefreshSession> findByIdForUpdate(
        @Param("sessionId") String sessionId
    );

    void deleteAllByUserId(Long userId);

    boolean existsByUserId(Long userId);
}