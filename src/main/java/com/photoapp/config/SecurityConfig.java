package com.photoapp.config;

import com.photoapp.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT-based security configuration. No HTTP sessions are created;
 * every request must carry a valid {@code Authorization: Bearer <jwt>}
 * header to access {@code /api/photos/**}. {@code /api/auth/login} is the
 * only public endpoint.
 *
 * <p>Note: credential verification for login is performed manually in
 * {@code AuthService} (using {@link PasswordEncoder} directly) rather than
 * through Spring Security's {@code AuthenticationManager}, since login also
 * needs custom account-lockout logic. No {@code AuthenticationProvider} /
 * {@code AuthenticationManager} bean is therefore declared here.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/photos/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
