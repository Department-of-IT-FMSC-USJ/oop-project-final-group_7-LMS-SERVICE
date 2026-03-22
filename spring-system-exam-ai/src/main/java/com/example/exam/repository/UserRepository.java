package com.example.exam.repository;

import com.example.exam.entity.Role;
import com.example.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
    long countByRole(Role role);
    long countByIsActiveTrue();
}
