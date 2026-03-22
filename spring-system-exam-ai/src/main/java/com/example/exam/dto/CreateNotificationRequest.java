package com.example.exam.dto;

import java.util.List;

public record CreateNotificationRequest(
        String title,
        String message,
        String notificationType,
        List<Long> recipientIds
) {
}
