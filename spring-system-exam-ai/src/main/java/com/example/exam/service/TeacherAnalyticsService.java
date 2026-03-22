package com.example.exam.service;

import com.example.exam.entity.ExamAttempt;
import com.example.exam.entity.StudentAnswer;
import com.example.exam.repository.ExamAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherAnalyticsService {

    private final ExamAttemptRepository examAttemptRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getExamAnalytics(Long examId) {
        List<ExamAttempt> attempts = examAttemptRepository.findByExamId(examId);

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("examId", examId);
        analytics.put("totalAttempts", attempts.size());

        if (attempts.isEmpty()) {
            analytics.put("averageScore", 0);
            analytics.put("highestScore", 0);
            analytics.put("lowestScore", 0);
            analytics.put("passRate", 0);
            analytics.put("students", List.of());
            return analytics;
        }

        DoubleSummaryStatistics stats = attempts.stream()
                .filter(a -> a.getTotalQuestions() != null && a.getTotalQuestions() > 0 && a.getScore() != null)
                .mapToDouble(a -> (double) a.getScore() / a.getTotalQuestions() * 100)
                .summaryStatistics();

        long passCount = attempts.stream()
                .filter(a -> a.getTotalQuestions() != null && a.getTotalQuestions() > 0 && a.getScore() != null && (double) a.getScore() / a.getTotalQuestions() >= 0.5)
                .count();

        analytics.put("averageScore", Math.round(stats.getAverage() * 100.0) / 100.0);
        analytics.put("highestScore", Math.round(stats.getMax() * 100.0) / 100.0);
        analytics.put("lowestScore", Math.round(stats.getMin() * 100.0) / 100.0);
        analytics.put("passRate", Math.round(passCount * 100.0 / attempts.size() * 100.0) / 100.0);

        // Score distribution buckets
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("0-20", attempts.stream().filter(a -> pct(a) < 20).count());
        distribution.put("20-40", attempts.stream().filter(a -> pct(a) >= 20 && pct(a) < 40).count());
        distribution.put("40-60", attempts.stream().filter(a -> pct(a) >= 40 && pct(a) < 60).count());
        distribution.put("60-80", attempts.stream().filter(a -> pct(a) >= 60 && pct(a) < 80).count());
        distribution.put("80-100", attempts.stream().filter(a -> pct(a) >= 80).count());
        analytics.put("scoreDistribution", distribution);

        // Per-student ranking
        List<Map<String, Object>> studentList = attempts.stream()
                .sorted(Comparator.comparingDouble(this::pct).reversed())
                .map(a -> {
                    Map<String, Object> sMap = new LinkedHashMap<>();
                    sMap.put("studentId", a.getStudent().getId());
                    sMap.put("studentName", a.getStudent().getUsername());
                    sMap.put("score", a.getScore());
                    sMap.put("totalQuestions", a.getTotalQuestions());
                    sMap.put("percentage", Math.round(pct(a) * 100.0) / 100.0);
                    sMap.put("submittedAt", a.getSubmittedAt());
                    return sMap;
                }).toList();

        // Add rank
        for (int i = 0; i < studentList.size(); i++) {
            studentList.get(i).put("rank", i + 1);
        }

        analytics.put("students", studentList);
        return analytics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentDetailedPerformance(Long examId, Long studentId) {
        List<ExamAttempt> attempts = examAttemptRepository.findByExamId(examId);
        ExamAttempt attempt = attempts.stream()
                .filter(a -> a.getStudent().getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Student has not taken this exam"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentName", attempt.getStudent().getUsername());
        result.put("score", attempt.getScore());
        result.put("totalQuestions", attempt.getTotalQuestions());
        result.put("percentage", attempt.getTotalQuestions() != null && attempt.getTotalQuestions() > 0 && attempt.getScore() != null
                ? Math.round((double) attempt.getScore() / attempt.getTotalQuestions() * 10000.0) / 100.0
                : 0);
        result.put("submittedAt", attempt.getSubmittedAt());

        // Per-question breakdown
        result.put("answers", attempt.getAnswers().stream().map(a -> {
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("questionText", a.getExamQuestion().getQuestion().getQuestionText());
            aMap.put("selectedAnswer", a.getSelectedAnswer());
            aMap.put("correctAnswer", a.getExamQuestion().getQuestion().getCorrectAnswer());
            aMap.put("correct", a.isCorrect());
            aMap.put("lessonName", a.getExamQuestion().getQuestion().getLessonName());
            return aMap;
        }).toList());

        // Per-lesson breakdown
        Map<String, int[]> lessonStats = new LinkedHashMap<>();
        for (StudentAnswer a : attempt.getAnswers()) {
            String lesson = a.getExamQuestion().getQuestion().getLessonName();
            if (lesson != null && !lesson.isBlank()) {
                lessonStats.computeIfAbsent(lesson, k -> new int[2]);
                lessonStats.get(lesson)[0]++; // total
                if (a.isCorrect()) lessonStats.get(lesson)[1]++; // correct
            }
        }
        result.put("lessonBreakdown", lessonStats.entrySet().stream().map(e -> {
            Map<String, Object> lMap = new LinkedHashMap<>();
            lMap.put("lessonName", e.getKey());
            lMap.put("total", e.getValue()[0]);
            lMap.put("correct", e.getValue()[1]);
            lMap.put("incorrect", e.getValue()[0] - e.getValue()[1]);
            return lMap;
        }).toList());

        return result;
    }

    private double pct(ExamAttempt a) {
        if (a.getTotalQuestions() == null || a.getTotalQuestions() == 0) return 0;
        int score = a.getScore() != null ? a.getScore() : 0;
        return (double) score / a.getTotalQuestions() * 100;
    }
}
