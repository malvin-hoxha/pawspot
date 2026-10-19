package com.pawspot.auth.refreshtoken.entity;

import java.time.Instant;
import java.util.UUID;

import com.pawspot.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    public RefreshSession(User user, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
