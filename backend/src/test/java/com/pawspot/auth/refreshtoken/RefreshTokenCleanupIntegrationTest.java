package com.pawspot.auth.refreshtoken;

import com.pawspot.auth.refreshtoken.entity.RefreshSession;
import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.service.RefreshTokenCleanupService;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RefreshTokenCleanupIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private RefreshTokenCleanupService cleanupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldKeepCleanupBeanWithoutEnablingAutomaticScheduling() {
        assertNotNull(cleanupService);
        assertFalse(applicationContext.containsBean(
            TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
        ));
    }

    @Test
    void shouldDeleteOnlyExpiredRefreshTokens() {

        // 1. Δημιουργούμε χρήστη
        User user = new User();
        user.setName("Cleanup Test User");
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);

        // 2. Ληγμένο token
        RefreshToken expired = createToken(
            savedUser,
            Instant.now().minusSeconds(3600),
            null
        );

        // 3. Revoked αλλά όχι ληγμένο
        RefreshToken revoked = createToken(
            savedUser,
            Instant.now().plusSeconds(3600),
            Instant.now()
        );

        // 4. Ενεργό token
        RefreshToken active = createToken(
            savedUser,
            Instant.now().plusSeconds(3600),
            null
        );

        expired = tokenRepository.saveAndFlush(expired);
        revoked = tokenRepository.saveAndFlush(revoked);
        active = tokenRepository.saveAndFlush(active);

        Long expiredId = expired.getId();
        Long revokedId = revoked.getId();
        Long activeId = active.getId();

        // 5. Εκτελούμε χειροκίνητα το scheduled cleanup
        cleanupService.cleanupExpiredTokens();

        // 6. Ελέγχουμε τα αποτελέσματα
        assertFalse(tokenRepository.existsById(expiredId));

        assertTrue(tokenRepository.existsById(revokedId));

        assertTrue(tokenRepository.existsById(activeId));
    }

    private RefreshToken createToken(
        User user,
        Instant expiresAt,
        Instant revokedAt
    ) {
        RefreshToken token = new RefreshToken();

        token.setTokenId(UUID.randomUUID().toString());
        token.setFamilyId(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);

        return token;
    }

    @Test
    void shouldDeleteOnlyExpiredSessions() {

        User user = new User();
        user.setName("Session Cleanup User");
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);

        Instant now = Instant.now();

        // Ληγμένη session
        RefreshSession expiredSession = new RefreshSession(
            savedUser,
            now.minusSeconds(3600)
        );

        // Ενεργή session
        RefreshSession activeSession = new RefreshSession(
            savedUser,
            now.plusSeconds(3600)
        );

        sessionRepository.saveAndFlush(expiredSession);
        sessionRepository.saveAndFlush(activeSession);

        String expiredId = expiredSession.getId();
        String activeId = activeSession.getId();

        // Εκτελεί και τα δύο cleanup operations
        cleanupService.cleanupExpiredTokens();

        assertFalse(sessionRepository.existsById(expiredId));
        assertTrue(sessionRepository.existsById(activeId));
    }
}
