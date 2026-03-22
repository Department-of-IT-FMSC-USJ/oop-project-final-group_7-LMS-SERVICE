package com.example.exam.service;

import com.example.exam.dto.ChangePasswordRequest;
import com.example.exam.dto.UpdateProfileRequest;
import com.example.exam.entity.User;
import com.example.exam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User getProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new RuntimeException("Username already taken");
            }
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new RuntimeException("Email already registered");
            }
            user.setEmail(request.email());
        }

        if (request.nic() != null) user.setNic(request.nic());
        if (request.dateOfBirth() != null) user.setDateOfBirth(request.dateOfBirth());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.address() != null) user.setAddress(request.address());
        if (request.contactNumber() != null) user.setContactNumber(request.contactNumber());
        if (request.profilePhoto() != null) user.setProfilePhoto(request.profilePhoto());

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new RuntimeException("New password and confirmation do not match");
        }

        if (request.newPassword().length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
