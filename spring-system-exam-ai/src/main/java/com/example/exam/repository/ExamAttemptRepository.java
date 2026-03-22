package com.example.exam.repository;

import com.example.exam.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    List<ExamAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);
    List<ExamAttempt> findByExamId(Long examId);
    long countByExamId(Long examId);
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
}
