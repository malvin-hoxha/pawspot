package com.pawspot.auth.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoginRateLimiterTest {
    private LoginRateLimiter limiter;
    private AtomicLong time;

    @BeforeEach
    void setUp() {
        time = new AtomicLong(0);
        limiter = new LoginRateLimiter(time::get);
    }

    @Test
    void shouldRejectTheTwentyFirstAttemptFromTheSameIp() {
        String ip = "192.0.2.1";

        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.checkIp(ip);
        }

        LoginRateLimitExceededException exception = assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkIp(ip)
        );

        assertEquals(429, exception.getStatusCode().value());

        long retryAfter = Long.parseLong(
            exception.getHeaders().getFirst("Retry-After")
        );

        assertTrue(retryAfter > 0 && retryAfter <= 60);
    }

    @Test
    void shouldShareTheEmailLimitAcrossDifferentLetterCases() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkEmail("test@example.com");
        }

        assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkEmail("TEST@example.com")
        );
    }

    @Test
    void shouldResetTheEmailCounterAfterSuccessfulLogin() {
        String email = "test@example.com";

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkEmail(email);
        }

        limiter.onLoginSuccess("TEST@example.com");

        // A successful login starts a fresh email attempt budget.
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkEmail(email);
        }

        assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkEmail(email)
        );
    }

    @Test
    void shouldPreserveTheIpCounterAfterSuccessfulLogin() {
        String ip = "192.0.2.1";

        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.checkIp(ip);
        }

        limiter.onLoginSuccess("test@example.com");

        assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkIp(ip)
        );
    }

    @Test
    void shouldAllowExactlyTwentyConcurrentAttemptsForTheSameIp()
            throws Exception {

        int accepted = runConcurrentAttempts(
            () -> limiter.checkIp("192.0.2.1")
        );

        assertEquals(20, accepted);
    }

    @Test
    void shouldAllowExactlyFiveConcurrentAttemptsForTheSameEmail()
            throws Exception {

        int accepted = runConcurrentAttempts(
            () -> limiter.checkEmail("test@example.com")
        );

        assertEquals(5, accepted);
    }

    private int runConcurrentAttempts(Runnable attempt) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 50; i++) {
                results.add(executor.submit(() -> {
                    // Let the submitted tasks start competing for the counter.
                    start.await();

                    try {
                        attempt.run();
                        return true;
                    } catch (LoginRateLimitExceededException exception) {
                        return false;
                    }
                }));
            }

            start.countDown();

            int accepted = 0;

            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            return accepted;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowIpAttemptsAgainWhenTheMinuteExpires() {
        String ip = "192.0.2.1";

        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.checkIp(ip);
        }

        time.set(TimeUnit.SECONDS.toNanos(59));

        LoginRateLimitExceededException exception = assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkIp(ip)
        );

        assertEquals(
            "1",
            exception.getHeaders().getFirst("Retry-After")
        );

        // Reach the expiration boundary without waiting in real time.
        time.set(TimeUnit.SECONDS.toNanos(60));

        assertDoesNotThrow(() -> limiter.checkIp(ip));
    }

    @Test
    void shouldAllowEmailAttemptsAgainWhenTenMinutesExpire() {
        String email = "test@example.com";

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkEmail(email);
        }

        time.set(TimeUnit.SECONDS.toNanos(599));

        LoginRateLimitExceededException exception = assertThrows(
            LoginRateLimitExceededException.class,
            () -> limiter.checkEmail(email)
        );

        assertEquals(
            "1",
            exception.getHeaders().getFirst("Retry-After")
        );

        time.set(TimeUnit.MINUTES.toNanos(10));

        assertDoesNotThrow(() -> limiter.checkEmail(email));
    }
}
