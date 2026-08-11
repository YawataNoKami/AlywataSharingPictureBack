package com.photoapp.dto;

/**
 * Response returned on successful authentication.
 *
 * @param token     the signed JWT
 * @param expiresAt epoch millis at which the token expires
 * @param username  the authenticated user's username
 */
public record LoginResponse(String token, long expiresAt, String username) {
}
