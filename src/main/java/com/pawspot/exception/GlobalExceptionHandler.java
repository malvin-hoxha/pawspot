package com.pawspot.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pawspot.exception.image.ImageUploadException;
import com.pawspot.exception.image.ImageValidationException;
import com.pawspot.exception.validation.PostValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(
        InvalidCredentialsException ex
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("message", ex.getMessage())); // { "message":"Invalid email or password"}
    }

    @ExceptionHandler(ImageUploadException.class)
    public ResponseEntity<Map<String, String>> handleImageUpload(
            ImageUploadException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<Map<String, String>> handleImageValidation(
        ImageValidationException ex
    ) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                    "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePostNotFound(
        PostNotFoundException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Map<String, String>> handlePostUpdate(
        ForbiddenOperationException ex
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of(
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(PostValidationException.class)
    public ResponseEntity<Map<String, String>> handlePostValidation(
            PostValidationException ex
    ) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .get(0)
            .getDefaultMessage();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "message", message
            ));
    }
}

//PostNotFoundException        → 404
//ForbiddenOperationException  → 403
//PostValidationException      → 400