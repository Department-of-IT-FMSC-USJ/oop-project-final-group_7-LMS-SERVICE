package com.example.exam.repository;

import com.example.exam.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<Course> findAllByOrderByCreatedAtDesc();
}
