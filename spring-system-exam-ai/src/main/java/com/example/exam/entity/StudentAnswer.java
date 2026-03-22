package com.example.exam.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "student_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_question_id", nullable = false)
    private ExamQuestion examQuestion;

    @Column(name = "selected_answer", columnDefinition = "TEXT")
    private String selectedAnswer;

    @Column(name = "is_correct")
    private boolean correct;
}
