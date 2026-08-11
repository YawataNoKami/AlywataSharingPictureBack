package com.photoapp.service;

import com.photoapp.exception.AccountLockedException;
import com.photoapp.exception.InvalidCredentialsException;
import com.photoapp.model.User;
import com.photoapp.repository.UserRepository;
import com.photoapp.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, MAX_ATTEMPTS, LOCK_MINUTES);
    }

    private User buildUser() {
        return User.builder()
                .id("user-1")
                .username("alice")
                .passwordHash("hashed-password")
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void login_withValidCredentials_returnsTokenAndResetsFailedAttempts() {
        var user = buildUser();
        user.setFailedLoginAttempts(2);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken("alice", "user-1")).thenReturn("signed-jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.login("alice", "correct-password");

        assertThat(response.token()).isEqualTo("signed-jwt-token");
        assertThat(response.username()).isEqualTo("alice");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentialsAndIncrementsCounter() {
        var user = buildUser();
        user.setFailedLoginAttempts(1);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> authService.login("alice", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getLockedUntil()).isNull();

        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void login_withUnknownUsername_throwsInvalidCredentials() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_afterFiveFailedAttempts_locksAccountFor15Minutes() {
        var user = buildUser();
        user.setFailedLoginAttempts(MAX_ATTEMPTS - 1); // one more failure reaches the threshold

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> authService.login("alice", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(captor.getValue().getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void login_whileAccountIsLocked_throwsAccountLockedException() {
        var user = buildUser();
        user.setFailedLoginAttempts(MAX_ATTEMPTS);
        user.setLockedUntil(Instant.now().plusSeconds(600));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("alice", "correct-password"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void login_whenLockHasExpired_resetsCounterAndAllowsRetry() {
        var user = buildUser();
        user.setFailedLoginAttempts(MAX_ATTEMPTS);
        user.setLockedUntil(Instant.now().minusSeconds(60)); // expired lock

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.login("alice", "correct-password");

        assertThat(response.token()).isEqualTo("token");
    }
}
