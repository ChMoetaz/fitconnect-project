package com.fitconnect.backend.controller;

import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.AuthResponse;
import com.fitconnect.backend.dto.LoginRequest;
import com.fitconnect.backend.dto.RegisterRequest;
import com.fitconnect.backend.dto.UserResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.security.JwtService;
import com.fitconnect.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        String token = jwtService.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(user, token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request);
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(AuthResponse.of(user, token));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        User user = userService.getById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
