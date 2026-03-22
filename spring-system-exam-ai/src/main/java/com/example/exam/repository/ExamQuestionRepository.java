package com.example.exam.repository;

import com.example.exam.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findByExamIdOrderByQuestionOrderAsc(Long examId);
}
