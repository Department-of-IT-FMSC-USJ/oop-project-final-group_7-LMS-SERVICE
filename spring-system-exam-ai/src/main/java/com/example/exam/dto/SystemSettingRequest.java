package com.example.exam.dto;

public record SystemSettingRequest(
        String settingKey,
        String settingValue,
        String category
) {
}
