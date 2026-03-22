package com.example.exam.dto;

public record AddQuestionRequest(
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctAnswer,
        String answerExplanation,
        String imageUrl,
        String lessonName,
        Double marks
) {
}
