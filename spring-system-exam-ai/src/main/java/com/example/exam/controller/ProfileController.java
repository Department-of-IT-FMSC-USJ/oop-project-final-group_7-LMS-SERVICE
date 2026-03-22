package com.example.exam.controller;

import com.example.exam.dto.ChangePasswordRequest;
import com.example.exam.dto.UpdateProfileRequest;
import com.example.exam.entity.User;
import com.example.exam.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal User user) {
        User profile = profileService.getProfile(user.getId());
        return ResponseEntity.ok(mapProfile(profile));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal User user,
                                           @RequestBody UpdateProfileRequest request) {
        User updated = profileService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully", "profile", mapProfile(updated)));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal User user,
                                            @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    private Map<String, Object> mapProfile(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name());
        map.put("nic", user.getNic());
        map.put("dateOfBirth", user.getDateOfBirth());
        map.put("gender", user.getGender());
        map.put("address", user.getAddress());
        map.put("contactNumber", user.getContactNumber());
        map.put("profilePhoto", user.getProfilePhoto());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }
}
