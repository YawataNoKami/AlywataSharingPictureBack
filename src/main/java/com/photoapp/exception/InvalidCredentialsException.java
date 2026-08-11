package com.photoapp.exception;

/** Thrown when a login attempt fails due to invalid username or password. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
