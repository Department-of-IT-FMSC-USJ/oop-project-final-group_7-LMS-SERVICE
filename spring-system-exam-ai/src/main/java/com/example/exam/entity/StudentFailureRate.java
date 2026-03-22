package com.example.exam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_failure_rates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "lesson_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentFailureRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "lesson_name", nullable = false)
    private String lessonName;

    @Builder.Default
    @Column(name = "bad_score", nullable = false)
    private Integer badScore = 0;

    @Builder.Default
    @Column(name = "total_wrong_count", nullable = false)
    private Integer totalWrongCount = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
