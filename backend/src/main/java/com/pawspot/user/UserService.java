package com.pawspot.user;
import com.pawspot.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service 
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDeletionTransactionService deletionTransactionService;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UserDeletionTransactionService deletionTransactionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.deletionTransactionService = deletionTransactionService;
    }

    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if(!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        deletionTransactionService.deleteUserData(userId);
    }
}
