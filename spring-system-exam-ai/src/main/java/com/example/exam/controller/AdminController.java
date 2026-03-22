package com.example.exam.controller;

import com.example.exam.dto.CreateNotificationRequest;
import com.example.exam.dto.SystemSettingRequest;
import com.example.exam.entity.*;
import com.example.exam.service.AdminService;
import com.example.exam.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final NotificationService notificationService;

    // ==================== User Management (FR-023) ====================

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String role) {
        if (role != null) {
            Role r = Role.valueOf(role.toUpperCase());
            return ResponseEntity.ok(adminService.getUsersByRole(r).stream().map(this::mapUser).toList());
        }
        return ResponseEntity.ok(adminService.getAllUsers().stream().map(this::mapUser).toList());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(mapUser(adminService.getUserById(id)));
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.ok(Map.of("message", "User activated successfully"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== System Monitoring (FR-024) ====================

    @GetMapping("/monitoring")
    public ResponseEntity<?> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    // ==================== Reports (FR-025) ====================

    @GetMapping("/reports/{reportType}")
    public ResponseEntity<?> generateReport(@PathVariable String reportType) {
        return ResponseEntity.ok(adminService.generateReport(reportType));
    }

    // ==================== System Settings (FR-026) ====================

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(@RequestParam(required = false) String category) {
        if (category != null) {
            SettingCategory cat = SettingCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(adminService.getSettingsByCategory(cat).stream().map(this::mapSetting).toList());
        }
        return ResponseEntity.ok(adminService.getAllSettings().stream().map(this::mapSetting).toList());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSetting(@AuthenticationPrincipal User admin,
                                           @RequestBody SystemSettingRequest request) {
        SystemSetting setting = adminService.updateSetting(admin, request);
        return ResponseEntity.ok(Map.of("message", "Setting updated successfully", "setting", mapSetting(setting)));
    }

    // ==================== Notifications (FR-022) ====================

    @PostMapping("/notifications/send")
    public ResponseEntity<?> sendNotification(@AuthenticationPrincipal User admin,
                                              @RequestBody CreateNotificationRequest request) {
        notificationService.sendToMultiple(admin, request);
        return ResponseEntity.ok(Map.of("message", "Notifications sent successfully"));
    }

    @PostMapping("/notifications/broadcast")
    public ResponseEntity<?> broadcastAnnouncement(@AuthenticationPrincipal User admin,
                                                   @RequestBody Map<String, String> request) {
        notificationService.sendSystemAnnouncement(admin, request.get("title"), request.get("message"));
        return ResponseEntity.ok(Map.of("message", "Announcement broadcast successfully"));
    }

    // ==================== Response Mappers ====================

    private Map<String, Object> mapUser(User user) {
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
        map.put("isActive", user.isActive());
        map.put("lockedUntil", user.getLockedUntil());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapSetting(SystemSetting setting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", setting.getId());
        map.put("settingKey", setting.getSettingKey());
        map.put("settingValue", setting.getSettingValue());
        map.put("category", setting.getCategory().name());
        map.put("updatedAt", setting.getUpdatedAt());
        return map;
    }
}
