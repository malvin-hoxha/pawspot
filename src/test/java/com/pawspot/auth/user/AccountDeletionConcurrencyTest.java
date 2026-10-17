
package com.pawspot.auth.user;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.service.RefreshTokenService;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.image.CloudinaryService;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;
import com.pawspot.user.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountDeletionConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:17");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    private User createUser() {
        User user = new User();
        user.setName("Concurrency Test User");
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        return userRepository.save(user);
    }

    private void startTogether(
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        assertTrue(
            ready.await(5, TimeUnit.SECONDS),
            "Workers were not ready"
        );

        start.countDown();
    }

    private void assertAccountDeleted(Long userId) {
        assertFalse(userRepository.existsById(userId));
        assertFalse(refreshTokenRepository.existsByUserId(userId));
        assertFalse(refreshSessionRepository.existsByUserId(userId));
    }

    @Test
    void shouldHandleConcurrentDeleteAndRefresh() throws Exception {
        User user = createUser();
        Long userId = user.getId();

        String refreshToken = refreshTokenService
            .createSession(user)
            .token();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> deleteFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));

                userService.deleteAccount(userId, "password123");
                return null;
            });

            Future<?> refreshFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));

                try {
                    refreshTokenService.rotate(refreshToken);
                } catch (InvalidCredentialsException ex) {
                    // Expected if account deletion wins.
                }

                return null;
            });

            startTogether(ready, start);

            deleteFuture.get(15, TimeUnit.SECONDS);
            refreshFuture.get(15, TimeUnit.SECONDS);

            assertAccountDeleted(userId);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldHandleConcurrentDeleteAndLogin() throws Exception {
        User user = createUser();
        Long userId = user.getId();
        String email = user.getEmail();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> deleteFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));

                userService.deleteAccount(userId, "password123");
                return null;
            });

            Future<Integer> loginFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));

                return mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s",
                                "password": "password123"
                            }
                            """.formatted(email))
                )
                .andReturn()
                .getResponse()
                .getStatus();
            });

            startTogether(ready, start);

            deleteFuture.get(15, TimeUnit.SECONDS);

            int loginStatus = loginFuture.get(15, TimeUnit.SECONDS);

            assertTrue(
                loginStatus == 200 || loginStatus == 401,
                "Unexpected login status: " + loginStatus
            );

            assertAccountDeleted(userId);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
