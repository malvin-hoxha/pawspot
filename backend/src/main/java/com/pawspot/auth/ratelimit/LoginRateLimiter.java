package com.pawspot.auth.ratelimit;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component 
public class LoginRateLimiter {

    //IP limit
    private static final int MAX_IP_ATTEMPTS = 20;
    private static final int MAX_TRACKED_IPS = 10_000;
    private static final Duration IP_WINDOW = Duration.ofMinutes(1);

    //Email limit
    private static final int MAX_EMAIL_ATTEMPTS = 5;
    private static final int MAX_TRACKED_EMAILS = 10_000;
    private static final Duration EMAIL_WINDOW = Duration.ofMinutes(10);

    private final Ticker ticker;
    private final Cache<String, AttemptWindow> ipAttempts;
    private final Cache<String, AttemptWindow> emailAttempts;

    public LoginRateLimiter() {
        this(Ticker.systemTicker());
    }

    // Tests can provide a controllable time source.
    LoginRateLimiter(Ticker ticker) {
        this.ticker = ticker;

        this.ipAttempts = Caffeine.newBuilder()
            .expireAfterWrite(IP_WINDOW)
            .scheduler(Scheduler.systemScheduler())
            .ticker(ticker)
            .build();

        this.emailAttempts = Caffeine.newBuilder()
            .expireAfterWrite(EMAIL_WINDOW)
            .scheduler(Scheduler.systemScheduler())
            .ticker(ticker)
            .build();
    }

    // Keep the counter check and increment together across concurrent requests.
    public synchronized void checkIp(String ip) {
        long now = ticker.read();
        AttemptWindow window = ipAttempts.getIfPresent(ip);

        if (window == null) {
            ipAttempts.cleanUp();

            // Preserve active counters when capacity is reached.
            // Discarding them would let some IPs restart their attempt limits.
            if (ipAttempts.estimatedSize() >= MAX_TRACKED_IPS) {
                throw new LoginRateLimitExceededException(
                    IP_WINDOW.toSeconds()
                );
            }
        }

        // Start a new fixed window for a new IP or an expired counter. 
        if (window == null || now >= window.expiresAtNanos) {
            window = new AttemptWindow(now + IP_WINDOW.toNanos());
            ipAttempts.put(ip, window);
        }

        if (window.attempts >= MAX_IP_ATTEMPTS) {
            long remainingNanos = window.expiresAtNanos - now;

            // Retry-After uses whole seconds; round up to avoid retrying early.
            long retryAfterSeconds =
                (remainingNanos + 999_999_999L) / 1_000_000_000L;

            throw new LoginRateLimitExceededException(retryAfterSeconds);
        }

        // Mutating the counter does not reset the cache entry's expiration.
        window.attempts++;
    }

    public synchronized void checkEmail(String email) {
        String key = normalizeEmail(email);
        long now = ticker.read();
        AttemptWindow window = emailAttempts.getIfPresent(key);

        if (window == null) {
            emailAttempts.cleanUp();

            // Preserve active counters when capacity is reached.
            if (emailAttempts.estimatedSize() >= MAX_TRACKED_EMAILS) {
                throw new LoginRateLimitExceededException(
                    EMAIL_WINDOW.toSeconds()
                );
            }
        }

        if (window == null || now >= window.expiresAtNanos) {
            window = new AttemptWindow(now + EMAIL_WINDOW.toNanos());
            emailAttempts.put(key, window);
        }

         if (window.attempts >= MAX_EMAIL_ATTEMPTS) {
            long remainingNanos = window.expiresAtNanos - now;

            // Retry-After must cover the entire remaining wait.
            long retryAfterSeconds =
                (remainingNanos + 999_999_999L) / 1_000_000_000L;

            throw new LoginRateLimitExceededException(retryAfterSeconds);
        }

        // Reserve an attempt before checking credentials.
        window.attempts++;
    }

    public synchronized void onLoginSuccess(String email) {
        // A successful login clears the email counter, but not the IP counter.
        emailAttempts.invalidate(normalizeEmail(email));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptWindow {

        private int attempts;
        private final long expiresAtNanos;

        private AttemptWindow(long expiresAtNanos) {
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
