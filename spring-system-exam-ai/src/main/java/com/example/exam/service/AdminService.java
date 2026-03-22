package com.example.exam.service;

import com.example.exam.dto.SystemSettingRequest;
import com.example.exam.entity.*;
import com.example.exam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSetRepository questionSetRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SystemSettingRepository systemSettingRepository;

    // ==================== User Management (FR-023) ====================

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    // ==================== System Monitoring (FR-024) ====================

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalStudents", userRepository.countByRole(Role.STUDENT));
        stats.put("totalTeachers", userRepository.countByRole(Role.TEACHER));
        stats.put("totalAdmins", userRepository.countByRole(Role.ADMIN));
        stats.put("activeUsers", userRepository.countByIsActiveTrue());
        stats.put("totalExams", examRepository.count());
        stats.put("publishedExams", examRepository.countByStatus(ExamStatus.PUBLISHED));
        stats.put("totalAttempts", examAttemptRepository.count());
        stats.put("totalQuestions", questionRepository.count());
        stats.put("totalQuestionSets", questionSetRepository.count());
        stats.put("recentLogins", loginAttemptRepository.findTop20ByOrderByAttemptedAtDesc().stream().map(la -> {
            Map<String, Object> loginMap = new LinkedHashMap<>();
            loginMap.put("username", la.getUsername());
            loginMap.put("successful", la.isSuccessful());
            loginMap.put("attemptedAt", la.getAttemptedAt());
            return loginMap;
        }).toList());
        return stats;
    }

    // ==================== Reports (FR-025) ====================

    @Transactional(readOnly = true)
    public Map<String, Object> generateReport(String reportType) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", reportType);

        switch (reportType.toLowerCase()) {
            case "student_performance" -> {
                List<ExamAttempt> allAttempts = examAttemptRepository.findAll();
                long totalAttempts = allAttempts.size();
                double avgScore = allAttempts.stream()
                        .filter(a -> a.getTotalQuestions() != null && a.getTotalQuestions() > 0 && a.getScore() != null)
                        .mapToDouble(a -> (double) a.getScore() / a.getTotalQuestions() * 100)
                        .average().orElse(0);
                long passCount = allAttempts.stream()
                        .filter(a -> a.getTotalQuestions() != null && a.getTotalQuestions() > 0 && a.getScore() != null && (double) a.getScore() / a.getTotalQuestions() >= 0.5)
                        .count();
                report.put("totalAttempts", totalAttempts);
                report.put("averageScorePercentage", Math.round(avgScore * 100.0) / 100.0);
                report.put("passRate", totalAttempts > 0 ? Math.round(passCount * 100.0 / totalAttempts * 100.0) / 100.0 : 0);
                report.put("failRate", totalAttempts > 0 ? Math.round((totalAttempts - passCount) * 100.0 / totalAttempts * 100.0) / 100.0 : 0);
            }
            case "exam_statistics" -> {
                List<Exam> allExams = examRepository.findAll();
                report.put("totalExams", allExams.size());
                report.put("draftExams", allExams.stream().filter(e -> e.getStatus() == ExamStatus.DRAFT).count());
                report.put("publishedExams", allExams.stream().filter(e -> e.getStatus() == ExamStatus.PUBLISHED).count());
                report.put("closedExams", allExams.stream().filter(e -> e.getStatus() == ExamStatus.CLOSED).count());
                report.put("exams", allExams.stream().map(e -> {
                    Map<String, Object> examMap = new LinkedHashMap<>();
                    examMap.put("id", e.getId());
                    examMap.put("title", e.getTitle());
                    examMap.put("status", e.getStatus());
                    examMap.put("teacherName", e.getTeacher().getUsername());
                    examMap.put("attemptCount", examAttemptRepository.countByExamId(e.getId()));
                    return examMap;
                }).toList());
            }
            case "teacher_activity" -> {
                List<User> teachers = userRepository.findByRole(Role.TEACHER);
                report.put("totalTeachers", teachers.size());
                report.put("teachers", teachers.stream().map(t -> {
                    Map<String, Object> tMap = new LinkedHashMap<>();
                    tMap.put("id", t.getId());
                    tMap.put("username", t.getUsername());
                    tMap.put("email", t.getEmail());
                    tMap.put("examCount", examRepository.findByTeacherIdOrderByCreatedAtDesc(t.getId()).size());
                    tMap.put("questionSetCount", questionSetRepository.findByTeacherIdOrderByCreatedAtDesc(t.getId()).size());
                    return tMap;
                }).toList());
            }
            case "system_usage" -> {
                report.put("totalUsers", userRepository.count());
                report.put("activeUsers", userRepository.countByIsActiveTrue());
                report.put("totalExams", examRepository.count());
                report.put("totalAttempts", examAttemptRepository.count());
                report.put("totalQuestions", questionRepository.count());
                report.put("totalQuestionSets", questionSetRepository.count());
            }
            default -> throw new RuntimeException("Unknown report type: " + reportType);
        }

        return report;
    }

    // ==================== System Settings (FR-026) ====================

    @Transactional(readOnly = true)
    public List<SystemSetting> getAllSettings() {
        return systemSettingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> getSettingsByCategory(SettingCategory category) {
        return systemSettingRepository.findByCategory(category);
    }

    @Transactional
    public SystemSetting updateSetting(User admin, SystemSettingRequest request) {
        SettingCategory category = SettingCategory.valueOf(request.category().toUpperCase());
        SystemSetting setting = systemSettingRepository.findBySettingKey(request.settingKey())
                .orElse(SystemSetting.builder()
                        .settingKey(request.settingKey())
                        .category(category)
                        .build());

        setting.setSettingValue(request.settingValue());
        setting.setCategory(category);
        setting.setUpdatedBy(admin);
        return systemSettingRepository.save(setting);
    }
}
