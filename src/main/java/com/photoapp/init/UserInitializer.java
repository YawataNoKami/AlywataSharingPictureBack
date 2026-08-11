package com.photoapp.init;

import com.photoapp.model.User;
import com.photoapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Provisions the two application accounts at startup from environment
 * variables ({@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} and
 * {@code PARTNER_USERNAME}/{@code PARTNER_PASSWORD}). There is no public
 * registration endpoint; accounts are only created if they do not already
 * exist, and passwords are never logged.
 */
@Component
public class UserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminUsername;
    private final String adminPassword;
    private final String partnerUsername;
    private final String partnerPassword;

    public UserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.init-users.admin.username}") String adminUsername,
            @Value("${app.init-users.admin.password}") String adminPassword,
            @Value("${app.init-users.partner.username}") String partnerUsername,
            @Value("${app.init-users.partner.password}") String partnerPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.partnerUsername = partnerUsername;
        this.partnerPassword = partnerPassword;
    }

    @Override
    public void run(String... args) {
        createIfAbsent(adminUsername, adminPassword, "Admin");
        createIfAbsent(partnerUsername, partnerPassword, "Partner");
    }

    private void createIfAbsent(String username, String password, String displayName) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Skipping creation of '{}' account: credentials not provided via environment variables", displayName);
            return;
        }
        if (userRepository.existsByUsername(username)) {
            log.info("Account '{}' already exists, skipping creation", username);
            return;
        }
        var user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName)
                .createdAt(Instant.now())
                .failedLoginAttempts(0)
                .build();
        userRepository.save(user);
        log.info("Created initial account '{}'", username);
    }
}
