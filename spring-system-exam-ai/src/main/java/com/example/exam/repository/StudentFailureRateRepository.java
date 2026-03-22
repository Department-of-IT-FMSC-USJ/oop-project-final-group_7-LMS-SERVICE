package com.example.exam.repository;

import com.example.exam.entity.StudentFailureRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentFailureRateRepository extends JpaRepository<StudentFailureRate, Long> {
    List<StudentFailureRate> findByStudentIdAndBadScoreGreaterThanOrderByBadScoreDesc(Long studentId, int minScore);
    List<StudentFailureRate> findByStudentIdOrderByBadScoreDesc(Long studentId);
    Optional<StudentFailureRate> findByStudentIdAndLessonName(Long studentId, String lessonName);
}
