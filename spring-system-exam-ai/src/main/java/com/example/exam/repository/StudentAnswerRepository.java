package com.example.exam.repository;

import com.example.exam.entity.StudentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {
}
