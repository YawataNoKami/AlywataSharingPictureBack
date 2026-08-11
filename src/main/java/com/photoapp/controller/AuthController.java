package com.photoapp.controller;

import com.photoapp.dto.LoginRequest;
import com.photoapp.dto.LoginResponse;
import com.photoapp.ratelimit.InMemoryRateLimiter;
import com.photoapp.ratelimit.RateLimitExceededException;
import com.photoapp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final InMemoryRateLimiter rateLimiter;
    private final int maxLoginAttemptsPerMinute;
    private final int loginRateWindowSeconds;

    public AuthController(
            AuthService authService,
            InMemoryRateLimiter rateLimiter,
            @Value("${app.rate-limit.login.max-attempts}") int maxLoginAttemptsPerMinute,
            @Value("${app.rate-limit.login.window-seconds}") int loginRateWindowSeconds) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.maxLoginAttemptsPerMinute = maxLoginAttemptsPerMinute;
        this.loginRateWindowSeconds = loginRateWindowSeconds;
    }

    /**
     * Authenticates a user and returns a JWT. Rate-limited to
     * {@code app.rate-limit.login.max-attempts} attempts per client IP per
     * {@code app.rate-limit.login.window-seconds} window, independent of
     * the per-account lockout enforced by {@link AuthService}.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var clientIp = extractClientIp(httpRequest);
        if (!rateLimiter.tryAcquire("login:" + clientIp, maxLoginAttemptsPerMinute, loginRateWindowSeconds)) {
            throw new RateLimitExceededException("Too many login attempts. Please try again later.");
        }
        var response = authService.login(request.username(), request.password());
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        var forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
