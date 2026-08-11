package com.photoapp.service;

import com.photoapp.dto.LoginResponse;
import com.photoapp.exception.AccountLockedException;
import com.photoapp.exception.InvalidCredentialsException;
import com.photoapp.model.User;
import com.photoapp.repository.UserRepository;
import com.photoapp.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Handles credential verification, account lockout and JWT issuance for
 * login requests. There is no registration endpoint: accounts are
 * provisioned once at application startup (see
 * {@code com.photoapp.init.UserInitializer}).
 *
 * <p>Lockout policy: after {@code app.auth.max-failed-attempts} consecutive
 * failed attempts, the account is locked for {@code app.auth.lock-duration-minutes}.
 * Once the lock expires, the failed-attempts counter is reset to zero on the
 * next login attempt (successful or not), so a stale lock never persists
 * indefinitely.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int maxFailedAttempts;
    private final long lockDurationMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.auth.max-failed-attempts}") int maxFailedAttempts,
            @Value("${app.auth.lock-duration-minutes}") long lockDurationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    /**
     * Authenticates a user by username/password. Never logs the submitted
     * password. Throws {@link AccountLockedException} if the account is
     * currently locked, or {@link InvalidCredentialsException} on any
     * other authentication failure (unknown user or wrong password) — the
     * same message is used for both cases to avoid user enumeration.
     */
    public LoginResponse login(String username, String password) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Login attempt for unknown username");
                    return new InvalidCredentialsException("Invalid username or password");
                });

        resetExpiredLock(user);

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(
                    "Account is temporarily locked due to too many failed login attempts",
                    user.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        var token = jwtService.generateToken(user.getUsername(), user.getId());
        var expiresAt = Instant.now().plusMillis(jwtService.getExpirationMs()).toEpochMilli();
        log.info("User '{}' logged in successfully", user.getUsername());
        return new LoginResponse(token, expiresAt, user.getUsername());
    }

    private void resetExpiredLock(User user) {
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(Instant.now())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
    }

    private void registerFailedAttempt(User user) {
        var attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(Instant.now().plusSeconds(lockDurationMinutes * 60));
            log.warn("User '{}' locked for {} minutes after {} failed attempts",
                    user.getUsername(), lockDurationMinutes, attempts);
        }
        userRepository.save(user);
    }
}
