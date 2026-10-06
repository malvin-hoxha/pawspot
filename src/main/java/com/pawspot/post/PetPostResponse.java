package com.pawspot.post;

import java.time.LocalDateTime;

public class PetPostResponse {
    private Long id;
    private PostType type;
    private String title;
    private String description;
    private PetSpecies species;
    private String city;
    private String area;
    private PostStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;
    private String userName;
    private String imageUrl;

    public PetPostResponse(
            Long id,
            PostType type,
            String title,
            String description,
            PetSpecies species,
            String city,
            String area,
            PostStatus status,
            String imageUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long userId,
            String userName
    ) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.species = species;
        this.city = city;
        this.area = area;
        this.status = status;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userId = userId;
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public PostType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public PetSpecies getSpecies() {
        return species;
    }

    public String getCity() {
        return city;
    }

    public String getArea() {
        return area;
    }

    public PostStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
