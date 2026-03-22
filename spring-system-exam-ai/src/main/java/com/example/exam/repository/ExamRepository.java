package com.example.exam.repository;

import com.example.exam.entity.Exam;
import com.example.exam.entity.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<Exam> findAllByOrderByCreatedAtDesc();
    List<Exam> findByStatusOrderByCreatedAtDesc(ExamStatus status);
    List<Exam> findByStatusAndStartDatetimeBeforeAndEndDatetimeAfter(
            ExamStatus status, LocalDateTime start, LocalDateTime end);
    long countByStatus(ExamStatus status);
    List<Exam> findByStatusAndCourseIdOrderByCreatedAtDesc(ExamStatus status, Long courseId);
    List<Exam> findByCourseIdIn(java.util.Collection<Long> courseIds);
}
