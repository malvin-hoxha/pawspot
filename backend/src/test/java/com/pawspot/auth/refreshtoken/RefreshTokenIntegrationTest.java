package com.pawspot.auth.refreshtoken;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawspot.auth.refreshtoken.entity.RefreshSession;
import com.pawspot.auth.refreshtoken.entity.RefreshToken;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.service.RefreshTokenService;
import com.pawspot.auth.response.RefreshResult;
import com.pawspot.security.JwtService;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import io.jsonwebtoken.Claims;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.exception.InvalidCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.Callable;

@SpringBootTest
class RefreshTokenIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldRotateRefreshToken() {
        // 1. Create test user
        User user = new User();
        user.setName("Rotation Test");
        user.setEmail("rotation-test@pawspot.com");
        user.setPassword("test-password");

        user = userRepository.save(user);

        // 2. Create initial refresh session (R1)
        String r1 = refreshTokenService.createSession(user).token();

        Claims r1Claims = jwtService.validateRefreshToken(r1);
        String r1TokenId = r1Claims.getId();

        // 3. Rotate R1 into R2
        RefreshResult result = refreshTokenService.rotate(r1);
        String r2 = result.refreshToken();

        Claims r2Claims = jwtService.validateRefreshToken(r2);
        String r2TokenId = r2Claims.getId();

        // 4. Verify R1 revoked, R2 active
        RefreshToken storedR1 =
            tokenRepository.findByTokenId(r1TokenId).orElseThrow();

        RefreshToken storedR2 =
            tokenRepository.findByTokenId(r2TokenId).orElseThrow();

        assertNotNull(storedR1.getRevokedAt());
        assertNull(storedR2.getRevokedAt());
        assertNotEquals(r1TokenId, r2TokenId);
    }

    @Test
    void shouldPreserveExpirationAfterRotation() {
        User user = new User();
        user.setName("Expiration Test");
        user.setEmail("expiration-test@pawspot.com");
        user.setPassword("test-password");
        user = userRepository.save(user);
        String r1 = refreshTokenService.createSession(user).token();

        Claims r1Claims = jwtService.validateRefreshToken(r1);

        RefreshResult result = refreshTokenService.rotate(r1);

        Claims r2Claims =
            jwtService.validateRefreshToken(result.refreshToken());

        assertEquals(
            r1Claims.getExpiration(),
            r2Claims.getExpiration()
        );

        RefreshToken storedR1 =
            tokenRepository.findByTokenId(r1Claims.getId()).orElseThrow();

        RefreshToken storedR2 =
            tokenRepository.findByTokenId(r2Claims.getId()).orElseThrow();

        assertEquals(
            storedR1.getExpiresAt(),
            storedR2.getExpiresAt()
        );

        assertEquals(
            storedR1.getFamilyId(),
            storedR2.getFamilyId()
        );
    }

    @Test
    void shouldRevokeEntireFamilyWhenRefreshTokenIsReused() {
        // 1. Create test user
        User user = new User();
        user.setName("Reuse Test");
        user.setEmail("reuse-test@pawspot.com");
        user.setPassword("test-password");
        user = userRepository.save(user);

        // 2. Create R1
        String r1 = refreshTokenService.createSession(user).token();

        Claims r1Claims = jwtService.validateRefreshToken(r1);
        String familyId = r1Claims.get("family_id", String.class);

        // 3. Rotate R1 -> R2
        RefreshResult result = refreshTokenService.rotate(r1);

        Claims r2Claims =
            jwtService.validateRefreshToken(result.refreshToken());

        // 4. Reuse R1: must fail
        assertThrows(
            InvalidCredentialsException.class,
            () -> refreshTokenService.rotate(r1)
        );

        // 5. Verify persisted revocation after the exception
        RefreshSession session = sessionRepository
            .findById(familyId)
            .orElseThrow();

        RefreshToken storedR1 = tokenRepository
            .findByTokenId(r1Claims.getId())
            .orElseThrow();

        RefreshToken storedR2 = tokenRepository
            .findByTokenId(r2Claims.getId())
            .orElseThrow();

        assertNotNull(session.getRevokedAt());
        assertNotNull(storedR1.getRevokedAt());
        assertNotNull(storedR2.getRevokedAt());
    }

    @Test
    void shouldRevokeSessionOnLogout() {
        User user = new User();
        user.setName("Logout Test");
        user.setEmail("logout-test@pawspot.com");
        user.setPassword("test-password");
        user = userRepository.save(user);

        // Create R1
        String r1 = refreshTokenService.createSession(user).token();

        Claims r1Claims = jwtService.validateRefreshToken(r1);
        String familyId = r1Claims.get("family_id", String.class);

        // Rotate R1 -> R2
        RefreshResult result = refreshTokenService.rotate(r1);
        String r2 = result.refreshToken();

        Claims r2Claims = jwtService.validateRefreshToken(r2);

        // Logout using R2
        refreshTokenService.logout(r2);

        // Verify persisted revocation
        RefreshSession session = sessionRepository
            .findById(familyId)
            .orElseThrow();

        RefreshToken storedR1 = tokenRepository
            .findByTokenId(r1Claims.getId())
            .orElseThrow();

        RefreshToken storedR2 = tokenRepository
            .findByTokenId(r2Claims.getId())
            .orElseThrow();

        assertNotNull(session.getRevokedAt());
        assertNotNull(storedR1.getRevokedAt());
        assertNotNull(storedR2.getRevokedAt());

        // R2 must no longer be usable
        assertThrows(
            InvalidCredentialsException.class,
            () -> refreshTokenService.rotate(r2)
        );
    }

    @Test
    void shouldHandleConcurrentRefreshRequests() throws Exception {
        // 1. Create user
        User user = new User();
        user.setName("Concurrency Test");
        user.setEmail("concurrency-test@pawspot.com");
        user.setPassword("test-password");
        user = userRepository.save(user);
        Long userId = user.getId();

        // 2. Create R1
        String r1 = refreshTokenService.createSession(user).token();

        // 3. Prepare two threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> refreshTask = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));

            try {
                refreshTokenService.rotate(r1);
                return true;
            } catch (InvalidCredentialsException ex) {
                return false;
            }
        };

        try {
            Future<Boolean> first = executor.submit(refreshTask);
            Future<Boolean> second = executor.submit(refreshTask);

            // Hold the row both rotations lock, and release it only after both
            // transactions are confirmed waiting in PostgreSQL.
            transactionTemplate.executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(userId).orElseThrow();

                try {
                    assertTrue(
                        ready.await(5, TimeUnit.SECONDS),
                        "Refresh workers were not ready"
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }

                start.countDown();
                awaitBlockedDatabaseTransactions(2);
            });

            boolean result1 = first.get(15, TimeUnit.SECONDS);
            boolean result2 = second.get(15, TimeUnit.SECONDS);

            // 5. Exactly one must succeed
            assertEquals(1, (result1 ? 1 : 0) + (result2 ? 1 : 0));

            // Verify committed database state
            Claims claims = jwtService.validateRefreshToken(r1);
            String familyId = claims.get("family_id", String.class);

            RefreshSession session = sessionRepository
                .findById(familyId)
                .orElseThrow();

            assertNotNull(session.getRevokedAt());

            List<RefreshToken> activeTokens = tokenRepository
                .findAllByFamilyIdAndRevokedAtIsNull(familyId);

            assertTrue(activeTokens.isEmpty());

            long familyTokenCount = tokenRepository.findAll()
                .stream()
                .filter(token -> token.getFamilyId().equals(familyId))
                .count();

            assertEquals(2, familyTokenCount);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor did not terminate within 5 seconds"
            );
        }
    }
}
