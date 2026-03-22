package com.example.exam.dto;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword,
        String confirmPassword
) {
}
