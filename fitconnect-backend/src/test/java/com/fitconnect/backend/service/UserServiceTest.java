package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.LoginRequest;
import com.fitconnect.backend.dto.RegisterRequest;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link UserService} — the repository and the password encoder are
 * mocked, so no database and no BCrypt cost. Covers the happy path plus every error branch
 * the service already handles ({@link BadRequestException} / {@link ResourceNotFoundException}).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void register_encodesPasswordAndPersistsWithUserRole() {
        when(userRepository.existsByEmail("new@fitconnect.test")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.register(registerRequest("new@fitconnect.test", "secret123"));

        assertThat(created.getEmail()).isEqualTo("new@fitconnect.test");
        assertThat(created.getPassword()).isEqualTo("ENCODED");
        assertThat(created.getRole()).isEqualTo("USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@fitconnect.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest("taken@fitconnect.test", "secret123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsUserWhenPasswordMatches() {
        User stored = User.builder().userId(1L).email("john@fitconnect.test").password("ENCODED").role("USER").build();
        when(userRepository.findByEmail("john@fitconnect.test")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("secret123", "ENCODED")).thenReturn(true);

        User result = userService.login(loginRequest("john@fitconnect.test", "secret123"));

        assertThat(result).isSameAs(stored);
    }

    @Test
    void login_rejectsUnknownEmailWithGenericMessage() {
        when(userRepository.findByEmail("ghost@fitconnect.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest("ghost@fitconnect.test", "secret123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_rejectsWrongPasswordWithGenericMessage() {
        User stored = User.builder().userId(1L).email("john@fitconnect.test").password("ENCODED").role("USER").build();
        when(userRepository.findByEmail("john@fitconnect.test")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest("john@fitconnect.test", "wrong")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void getById_returnsUserWhenPresent() {
        User stored = User.builder().userId(42L).email("john@fitconnect.test").role("USER").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(stored));

        assertThat(userService.getById(42L)).isSameAs(stored);
    }

    @Test
    void getById_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}