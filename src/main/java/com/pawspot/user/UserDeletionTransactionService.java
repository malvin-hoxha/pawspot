package com.pawspot.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;

@Service
public class UserDeletionTransactionService {
    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshSessionRepository refreshSessionRepository;

    public UserDeletionTransactionService(
        UserRepository userRepository,
        PetPostRepository petPostRepository,
        RefreshTokenRepository refreshTokenRepository,
        RefreshSessionRepository refreshSessionRepository
    ) {
        this.userRepository = userRepository;
        this.petPostRepository = petPostRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshSessionRepository = refreshSessionRepository;
    }

    @Transactional 
    public List<String> deleteUserData(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow();

        List<PetPost> posts = petPostRepository.findByUserId(userId);

        List<String> imagePublicIds = posts.stream()
            .map(PetPost::getImagePublicId)
            .filter(id -> id != null && !id.isBlank())
            .toList();

        refreshTokenRepository.deleteAllByUserId(userId);
        refreshSessionRepository.deleteAllByUserId(userId);

        petPostRepository.deleteAll(posts);
        userRepository.delete(user); 

        return imagePublicIds;
    }

    
}
