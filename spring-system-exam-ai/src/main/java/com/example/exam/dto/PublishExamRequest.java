package com.example.exam.dto;

import java.util.List;

public record PublishExamRequest(
        String title,
        String description,
        Integer durationMinutes,
        Integer questionCount,
        List<Long> questionSetIds,
        List<Long> questionIds,
        String startDatetime,
        String endDatetime,
        Long courseId
) {
}
