package com.pawspot.image.cleanup;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "image_deletion_tasks")
public class ImageDeletionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    public ImageDeletionTask(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException(
                "Image public ID must not be blank"
            );
        }

        this.publicId = publicId;
    }

    @PrePersist 
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;

        // New tasks are eligible for processing immediately.
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }
}
