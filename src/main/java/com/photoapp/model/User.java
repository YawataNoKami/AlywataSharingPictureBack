package com.photoapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Application user. This is a private, 2-user application: no public
 * registration is exposed. Accounts are provisioned at startup from
 * environment variables (see {@code com.photoapp.init.UserInitializer}).
 */
@Document(collection = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    /** BCrypt hash (cost factor 12). Never expose or log this field. */
    private String passwordHash;

    private String displayName;

    private Instant createdAt;

    private Instant lastLoginAt;

    private int failedLoginAttempts;

    /** Null when the account is not locked. */
    private Instant lockedUntil;
}
