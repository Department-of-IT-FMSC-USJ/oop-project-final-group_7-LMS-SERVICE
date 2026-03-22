package com.example.exam.dto;

import java.util.List;

public record SubmitExamRequest(List<AnswerSubmission> answers) {

    public record AnswerSubmission(Long examQuestionId, String selectedAnswer) {
    }
}
