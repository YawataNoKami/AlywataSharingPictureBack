package com.photoapp.exception;

/** Wraps low-level cryptographic failures (never leaks key material in the message). */
public class EncryptionException extends RuntimeException {
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
