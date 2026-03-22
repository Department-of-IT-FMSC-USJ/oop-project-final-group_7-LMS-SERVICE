package com.example.exam.repository;

import com.example.exam.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    long countByUsernameAndIsSuccessfulFalseAndAttemptedAtAfter(String username, LocalDateTime after);
    List<LoginAttempt> findTop20ByOrderByAttemptedAtDesc();
}
