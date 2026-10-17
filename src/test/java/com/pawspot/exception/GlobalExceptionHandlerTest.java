
package com.pawspot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
        new GlobalExceptionHandler();

    @Test
    void shouldReturn409ForOptimisticLockingConflict() {
        OptimisticLockingFailureException exception =
            new OptimisticLockingFailureException(
                "Version conflict"
            );

        var response = handler.handleOptimisticLocking(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
            "This post was modified by another request",
            response.getBody().get("message")
        );
    }
}
