package com.photoapp.exception;

import java.time.Instant;

/** Thrown when a login attempt is made against a temporarily locked account. */
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(String message, Instant lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
