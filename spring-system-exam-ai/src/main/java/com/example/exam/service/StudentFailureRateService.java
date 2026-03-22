package com.example.exam.service;

import com.example.exam.entity.StudentFailureRate;
import com.example.exam.entity.User;
import com.example.exam.repository.StudentFailureRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentFailureRateService {

    private final StudentFailureRateRepository failureRateRepository;

    @Transactional(readOnly = true)
    public List<StudentFailureRate> getWeakLessons(Long studentId) {
        return failureRateRepository.findByStudentIdAndBadScoreGreaterThanOrderByBadScoreDesc(studentId, 0);
    }

    @Transactional(readOnly = true)
    public List<StudentFailureRate> getAllLessons(Long studentId) {
        return failureRateRepository.findByStudentIdOrderByBadScoreDesc(studentId);
    }

    @Transactional
    public void incrementBadScore(User student, String lessonName) {
        if (lessonName == null || lessonName.isBlank()) return;

        Optional<StudentFailureRate> existing =
                failureRateRepository.findByStudentIdAndLessonName(student.getId(), lessonName);

        if (existing.isPresent()) {
            StudentFailureRate rate = existing.get();
            rate.setBadScore(rate.getBadScore() + 1);
            rate.setTotalWrongCount(rate.getTotalWrongCount() + 1);
            failureRateRepository.save(rate);
        } else {
            StudentFailureRate rate = StudentFailureRate.builder()
                    .student(student)
                    .lessonName(lessonName)
                    .badScore(1)
                    .totalWrongCount(1)
                    .build();
            failureRateRepository.save(rate);
        }
    }

    @Transactional
    public void decrementBadScore(User student, String lessonName) {
        if (lessonName == null || lessonName.isBlank()) return;

        Optional<StudentFailureRate> existing =
                failureRateRepository.findByStudentIdAndLessonName(student.getId(), lessonName);

        if (existing.isPresent()) {
            StudentFailureRate rate = existing.get();
            rate.setBadScore(Math.max(0, rate.getBadScore() - 1));
            failureRateRepository.save(rate);
        }
    }
}
