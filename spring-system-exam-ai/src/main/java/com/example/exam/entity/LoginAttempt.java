package com.example.exam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String username;

    @Column(name = "is_successful", nullable = false)
    private boolean isSuccessful;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        attemptedAt = LocalDateTime.now();
    }
}
