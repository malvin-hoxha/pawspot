package com.pawspot.auth.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class LoginRateLimitExceededException extends ResponseStatusException{
    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterSeconds) {
        super(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many login attempts. Please try again later."
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    //Headers
    //HTTP/1.1 429 Too Many Requests
    //Retry-After: 60
    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
