package com.example.exam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExamAiResponse(
        boolean success,
        @JsonProperty("total_questions") int totalQuestions,
        List<ExamAiQuestion> questions
) {

    public record ExamAiQuestion(
            @JsonProperty("question_number") String questionNumber,
            @JsonProperty("page_number") int pageNumber,
            @JsonProperty("question_text") String questionText,
            List<String> options,
            @JsonProperty("correct_answer") String correctAnswer,
            @JsonProperty("answer_explanation") String answerExplanation,
            @JsonProperty("has_shared_reference") boolean hasSharedReference,
            @JsonProperty("shared_reference_id") String sharedReferenceId,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("has_visual_options") boolean hasVisualOptions
    ) {
    }
}
