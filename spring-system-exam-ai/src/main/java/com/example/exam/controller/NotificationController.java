package com.example.exam.controller;

import com.example.exam.entity.Notification;
import com.example.exam.entity.User;
import com.example.exam.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId())
                .stream().map(this::mapNotification).toList());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(user.getId())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal User user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    private Map<String, Object> mapNotification(Notification n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("type", n.getNotificationType().name());
        map.put("title", n.getTitle());
        map.put("message", n.getMessage());
        map.put("isRead", n.isRead());
        map.put("senderName", n.getSender() != null ? n.getSender().getUsername() : "System");
        map.put("createdAt", n.getCreatedAt());
        return map;
    }
}
