package com.example.exam.service;

import com.example.exam.dto.CreateNotificationRequest;
import com.example.exam.entity.Notification;
import com.example.exam.entity.NotificationType;
import com.example.exam.entity.User;
import com.example.exam.repository.NotificationRepository;
import com.example.exam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void sendNotification(User sender, Long recipientId, NotificationType type, String title, String message) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Notification notification = Notification.builder()
                .sender(sender)
                .recipient(recipient)
                .notificationType(type)
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendToMultiple(User sender, CreateNotificationRequest request) {
        NotificationType type = NotificationType.valueOf(request.notificationType().toUpperCase());
        for (Long recipientId : request.recipientIds()) {
            sendNotification(sender, recipientId, type, request.title(), request.message());
        }
    }

    @Transactional
    public void sendSystemAnnouncement(User admin, String title, String message) {
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (!user.getId().equals(admin.getId())) {
                Notification notification = Notification.builder()
                        .sender(admin)
                        .recipient(user)
                        .notificationType(NotificationType.SYSTEM_ANNOUNCEMENT)
                        .title(title)
                        .message(message)
                        .build();
                notificationRepository.save(notification);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
        for (Notification n : notifications) {
            if (!n.isRead()) {
                n.setRead(true);
            }
        }
        notificationRepository.saveAll(notifications);
    }
}
