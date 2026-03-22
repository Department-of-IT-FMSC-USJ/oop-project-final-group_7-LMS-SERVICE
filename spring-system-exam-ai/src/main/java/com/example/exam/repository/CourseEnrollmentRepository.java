package com.example.exam.repository;

import com.example.exam.entity.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    List<CourseEnrollment> findByStudentId(Long studentId);
    List<CourseEnrollment> findByCourseId(Long courseId);
    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);
    Optional<CourseEnrollment> findByCourseIdAndStudentId(Long courseId, Long studentId);
    long countByCourseId(Long courseId);
}
