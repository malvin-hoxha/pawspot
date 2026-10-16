package com.pawspot.auth.refreshtoken.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawspot.auth.refreshtoken.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenId(String tokenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rt
        FROM RefreshToken rt
        WHERE rt.tokenId = :tokenId
    """)
    Optional<RefreshToken> findByTokenIdForUpdate(
        @Param("tokenId") String tokenId
    );

    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(
        String familyId
    );

    void deleteAllByUserId(Long userId);

    boolean existsByUserId(Long userId);
}