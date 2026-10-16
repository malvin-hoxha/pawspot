package com.pawspot.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.pawspot.auth.refreshtoken.service.RefreshTokenService;
import com.pawspot.auth.request.LoginRequest;
import com.pawspot.auth.request.RegisterRequest;
import com.pawspot.auth.response.LoginResult;
import com.pawspot.auth.response.UserResponse;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.security.GeneratedRefreshToken;
import com.pawspot.security.JwtService;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import com.pawspot.exception.email.EmailAlreadyExistsException;

//User registration, credential validation, and login
@Service 
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public User register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPassword(
            passwordEncoder.encode(request.getPassword())
        );

        try {
            return userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException ex) {

            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException();
            }

            throw ex;
        }
    }

    public LoginResult login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) 
            throw new InvalidCredentialsException("Invalid email or password");

        GeneratedRefreshToken generatedRefreshToken = refreshTokenService.createSession(user);

        String accessToken = jwtService.generateAccessToken(user);

        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getCreatedAt()
        );

        return new LoginResult(accessToken, generatedRefreshToken.token(), userResponse);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);//doesn't depend on the server language
    }
}
