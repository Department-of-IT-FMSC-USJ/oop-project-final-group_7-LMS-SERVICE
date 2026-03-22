package com.example.exam.dto;

import java.util.List;

public record CreateExamRequest(
        String title,
        String description,
        Integer durationMinutes,
        List<Long> questionIds
) {
}
