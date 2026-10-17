
package com.pawspot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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


    @Test
    void shouldReturn413WhenUploadExceedsLimit() {
        MaxUploadSizeExceededException exception =
            new MaxUploadSizeExceededException(5 * 1024 * 1024);

        var response = handler.handleMaxUploadSize(exception);

        assertEquals(
            HttpStatus.PAYLOAD_TOO_LARGE,
            response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
            "Uploaded file exceeds the maximum allowed size",
            response.getBody().get("message")
        );
    }

}
