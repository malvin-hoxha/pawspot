package com.pawspot.auth.response;

import java.time.LocalDateTime;

import com.pawspot.user.enums.UserRole;

public class UserResponse {
    
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private UserRole role;

    public UserResponse(Long id, String name, String email, LocalDateTime createdAt, UserRole role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public UserRole getRole() {
        return role;
    }
}
