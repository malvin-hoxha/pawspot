package com.pawspot.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.image.cleanup.ImageDeletionQueueService;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;

@Service
public class UserDeletionTransactionService {
    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final ImageDeletionQueueService imageDeletionQueueService;

    public UserDeletionTransactionService(
        UserRepository userRepository,
        PetPostRepository petPostRepository,
        RefreshTokenRepository refreshTokenRepository,
        RefreshSessionRepository refreshSessionRepository,
        ImageDeletionQueueService imageDeletionQueueService
    ) {
        this.userRepository = userRepository;
        this.petPostRepository = petPostRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.imageDeletionQueueService = imageDeletionQueueService;
    }

    @Transactional 
    public void deleteUserData(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow();

        List<PetPost> posts = petPostRepository.findByUserId(userId);

        for (PetPost post : posts) {
            imageDeletionQueueService.enqueue(post.getImagePublicId());
        }

        refreshTokenRepository.deleteAllByUserId(userId);
        refreshSessionRepository.deleteAllByUserId(userId);

        petPostRepository.deleteAll(posts);
        userRepository.delete(user); 
    }

    
}
