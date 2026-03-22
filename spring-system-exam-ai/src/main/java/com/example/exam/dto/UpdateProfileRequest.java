package com.example.exam.dto;

import java.time.LocalDate;

public record UpdateProfileRequest(
        String username,
        String email,
        String nic,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String contactNumber,
        String profilePhoto
) {
}
