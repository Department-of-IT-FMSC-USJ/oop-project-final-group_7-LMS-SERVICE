package com.example.exam.dto;

import java.time.LocalDate;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String role,
        String nic,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String contactNumber,
        String profilePhoto
) {
}
