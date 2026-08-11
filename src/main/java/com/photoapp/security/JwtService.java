package com.photoapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Issues and validates stateless JWTs used to authenticate API requests.
 * Tokens are signed with HMAC-SHA using a secret read from {@code JWT_SECRET}
 * (must be at least 256 bits). There is no server-side session store and no
 * refresh token: once a token expires (24h by default) the user must log in
 * again.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        var keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /** Generates a signed JWT for the given username, expiring after the configured duration. */
    public String generateToken(String username, String userId) {
        var now = Instant.now();
        var expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("uid", String.class));
    }

    /**
     * Validates the token's signature and expiration, and checks that its
     * subject matches {@code expectedUsername}.
     */
    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            var username = extractUsername(token);
            return username.equals(expectedUsername) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        var claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
