package com.example.exam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_set_id", nullable = false)
    private QuestionSet questionSet;

    @Column(name = "question_number")
    private String questionNumber;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text", columnDefinition = "TEXT")
    @OrderColumn(name = "option_order")
    @Builder.Default
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "answer_explanation", columnDefinition = "TEXT")
    private String answerExplanation;

    @Column(name = "has_shared_reference")
    private boolean hasSharedReference;

    @Column(name = "shared_reference_id")
    private String sharedReferenceId;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "has_visual_options")
    private boolean hasVisualOptions;

    @Column(name = "lesson_name")
    private String lessonName;

    @Builder.Default
    @Column(nullable = false)
    private Double marks = 1.0;
}
