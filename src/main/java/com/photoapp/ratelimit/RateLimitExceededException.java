package com.photoapp.ratelimit;

/** Thrown when a client exceeds the allowed request rate for a rate-limited endpoint. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
