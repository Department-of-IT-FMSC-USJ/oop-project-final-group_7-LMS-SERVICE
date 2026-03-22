package com.example.exam.repository;

import com.example.exam.entity.QuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    List<QuestionSet> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
