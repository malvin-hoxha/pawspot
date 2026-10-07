package com.pawspot.exception.validation;

public class PostValidationException extends RuntimeException{
    public PostValidationException(String message) {
        super(message);
    }
}
