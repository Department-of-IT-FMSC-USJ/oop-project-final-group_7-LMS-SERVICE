package com.example.exam.service;

import com.example.exam.dto.AuthResponse;
import com.example.exam.dto.LoginRequest;
import com.example.exam.dto.RegisterRequest;
import com.example.exam.entity.LoginAttempt;
import com.example.exam.entity.Role;
import com.example.exam.entity.User;
import com.example.exam.repository.LoginAttemptRepository;
import com.example.exam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptRepository loginAttemptRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.valueOf(request.role().toUpperCase()))
                .nic(request.nic())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .address(request.address())
                .contactNumber(request.contactNumber())
                .profilePhoto(request.profilePhoto())
                .build();

        userRepository.save(user);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String loginIdentifier = request.email() == null ? null : request.email().trim();
        if (loginIdentifier == null || loginIdentifier.isBlank()) {
            throw new RuntimeException("Email is required");
        }

        User user = userRepository.findByEmail(loginIdentifier)
            .or(() -> userRepository.findByUsername(loginIdentifier))
            .orElse(null);

        // Check if account is locked
        if (user != null) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new RuntimeException("Account is locked. Try again after " + user.getLockedUntil());
            }
            if (!user.isActive()) {
                throw new RuntimeException("Account is deactivated. Contact an administrator.");
            }
        }

        try {
            String principal = user != null ? user.getUsername() : loginIdentifier;
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(principal, request.password()));

            user = userRepository.findByUsername(principal)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Clear lock on successful login
            if (user.getLockedUntil() != null) {
                user.setLockedUntil(null);
                userRepository.save(user);
            }

            // Log successful attempt
            loginAttemptRepository.save(LoginAttempt.builder()
                    .user(user)
                    .username(user.getUsername())
                    .isSuccessful(true)
                    .build());

            String token = jwtService.generateToken(user);
            return new AuthResponse(token, user.getUsername(), user.getRole().name());

        } catch (Exception e) {
            String attemptedIdentifier = user != null ? user.getUsername() : loginIdentifier;

            // Log failed attempt
            loginAttemptRepository.save(LoginAttempt.builder()
                    .user(user)
                .username(attemptedIdentifier)
                    .isSuccessful(false)
                    .build());

            // Check if should lock account
            if (user != null) {
                long recentFailures = loginAttemptRepository
                        .countByUsernameAndIsSuccessfulFalseAndAttemptedAtAfter(
                                user.getUsername(),
                                LocalDateTime.now().minusMinutes(LOCKOUT_MINUTES));
                if (recentFailures >= MAX_FAILED_ATTEMPTS) {
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                    userRepository.save(user);
                    throw new RuntimeException("Account locked due to too many failed attempts. Try again in " + LOCKOUT_MINUTES + " minutes.");
                }
            }

            throw new RuntimeException("Invalid email or password");
        }
    }
}
