package com.pawspot.user;

import java.util.List;

import com.pawspot.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pawspot.image.CloudinaryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service 
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDeletionTransactionService deletionTransactionService;
    private final CloudinaryService cloudinaryService;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UserDeletionTransactionService deletionTransactionService,
        CloudinaryService cloudinaryService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.deletionTransactionService = deletionTransactionService;
        this.cloudinaryService = cloudinaryService;
    }

    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if(!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        List<String> imagePublicIds = deletionTransactionService.deleteUserData(userId);

        for (String publicId : imagePublicIds) {
            try {
                cloudinaryService.delete(publicId);
            } catch (RuntimeException ex) {
                log.error(
                    "Failed to delete Cloudinary image after account deletion: {}",
                    publicId,
                    ex
                );
            }
        }
    }
}
