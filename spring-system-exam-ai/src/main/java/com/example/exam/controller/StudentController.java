package com.example.exam.controller;

import com.example.exam.dto.SubmitExamRequest;
import com.example.exam.entity.*;
import com.example.exam.repository.CourseRepository;
import com.example.exam.repository.CourseEnrollmentRepository;
import com.example.exam.service.ExamService;
import com.example.exam.service.StudentFailureRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final ExamService examService;
    private final StudentFailureRateService failureRateService;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    /**
     * FR-012: List published exams for enrolled courses only.
     */
    @GetMapping("/exams")
    public ResponseEntity<?> getAvailableExams(@AuthenticationPrincipal User student) {
        List<CourseEnrollment> enrollments = courseEnrollmentRepository.findByStudentId(student.getId());
        List<Long> courseIds = enrollments.stream().map(e -> e.getCourse().getId()).toList();
        List<Exam> exams;
        if (courseIds.isEmpty()) {
            exams = List.of();
        } else {
            exams = examService.getPublishedExamsByCourseIds(courseIds);
        }
        return ResponseEntity.ok(exams.stream().map(this::mapExamForStudent).toList());
    }

    /**
     * FR-013: Get exam info (without questions — use POST /start to get questions).
     */
    @GetMapping("/exams/{id}")
    public ResponseEntity<?> getExam(@AuthenticationPrincipal User student, @PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        Map<String, Object> result = mapExamForStudent(exam);
        result.put("totalPoolQuestions", exam.getExamQuestions().size());
        return ResponseEntity.ok(result);
    }

    /**
     * FR-013: Start an exam — generates random/adaptive questions and persists them.
     */
    @PostMapping("/exams/{id}/start")
    public ResponseEntity<?> startExam(@AuthenticationPrincipal User student, @PathVariable Long id) {
        ExamAttempt attempt = examService.startExam(student, id);
        List<ExamQuestion> questions = examService.getQuestionsForAttempt(attempt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("examId", id);
        result.put("examTitle", attempt.getExam().getTitle());
        result.put("totalQuestions", attempt.getTotalQuestions());
        result.put("durationMinutes", attempt.getExam().getDurationMinutes());
        result.put("startedAt", attempt.getStartedAt());
        result.put("questions", questions.stream().map(this::mapExamQuestionForStudent).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve saved questions for an active attempt (e.g. on page reload).
     */
    @GetMapping("/attempts/{attemptId}/questions")
    public ResponseEntity<?> getAttemptQuestions(@AuthenticationPrincipal User student,
                                                 @PathVariable Long attemptId) {
        ExamAttempt attempt = examService.getAttemptById(attemptId);
        if (!attempt.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        if (attempt.getSubmittedAt() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "This attempt has already been submitted"));
        }
        List<ExamQuestion> questions = examService.getQuestionsForAttempt(attempt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("examId", attempt.getExam().getId());
        result.put("examTitle", attempt.getExam().getTitle());
        result.put("totalQuestions", attempt.getTotalQuestions());
        result.put("durationMinutes", attempt.getExam().getDurationMinutes());
        result.put("startedAt", attempt.getStartedAt());
        result.put("questions", questions.stream().map(this::mapExamQuestionForStudent).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * FR-015 + FR-016: Submit exam answers and get score.
     */
    @PostMapping("/exams/{id}/submit")
    public ResponseEntity<?> submitExam(
            @AuthenticationPrincipal User student,
            @PathVariable Long id,
            @RequestBody SubmitExamRequest request) {
        ExamAttempt attempt = examService.submitExam(student, id, request);
        return ResponseEntity.ok(mapAttemptResult(attempt));
    }

    /**
     * FR-019: List all my past attempts.
     */
    @GetMapping("/attempts")
    public ResponseEntity<?> getMyAttempts(@AuthenticationPrincipal User student) {
        List<ExamAttempt> attempts = examService.getStudentAttempts(student.getId());
        return ResponseEntity.ok(attempts.stream().map(this::mapAttemptSummary).toList());
    }

    /**
     * FR-017: Get detailed result of a specific attempt.
     */
    @GetMapping("/attempts/{id}")
    public ResponseEntity<?> getAttempt(@AuthenticationPrincipal User student,
                                        @PathVariable Long id) {
        ExamAttempt attempt = examService.getAttemptById(id);
        if (!attempt.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(mapAttemptResult(attempt));
    }

    /**
     * FR-018: View weak lessons dashboard.
     */
    @GetMapping("/weak-lessons")
    public ResponseEntity<?> getWeakLessons(@AuthenticationPrincipal User student) {
        List<StudentFailureRate> weakLessons = failureRateService.getWeakLessons(student.getId());
        List<StudentFailureRate> allLessons = failureRateService.getAllLessons(student.getId());

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("weakLessons", weakLessons.stream().map(this::mapFailureRate).toList());
        dashboard.put("masteredLessons", allLessons.stream()
                .filter(l -> l.getBadScore() == 0)
                .map(this::mapFailureRate).toList());
        return ResponseEntity.ok(dashboard);
    }

    // ==================== Courses ====================

    @GetMapping("/courses")
    public ResponseEntity<?> browseCourses(@AuthenticationPrincipal User student) {
        List<Course> all = courseRepository.findAllByOrderByCreatedAtDesc();
        List<CourseEnrollment> myEnrollments = courseEnrollmentRepository.findByStudentId(student.getId());
        Set<Long> enrolledIds = myEnrollments.stream().map(e -> e.getCourse().getId()).collect(Collectors.toSet());

        List<Map<String, Object>> result = all.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("description", c.getDescription());
            map.put("teacherName", c.getTeacher().getUsername());
            map.put("enrolledCount", courseEnrollmentRepository.countByCourseId(c.getId()));
            map.put("enrolled", enrolledIds.contains(c.getId()));
            map.put("createdAt", c.getCreatedAt());
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/courses/enrolled")
    public ResponseEntity<?> myEnrolledCourses(@AuthenticationPrincipal User student) {
        List<CourseEnrollment> enrollments = courseEnrollmentRepository.findByStudentId(student.getId());
        List<Map<String, Object>> result = enrollments.stream().map(e -> {
            Course c = e.getCourse();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("description", c.getDescription());
            map.put("teacherName", c.getTeacher().getUsername());
            map.put("enrolledAt", e.getEnrolledAt());
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/courses/{id}/enroll")
    public ResponseEntity<?> enrollInCourse(@AuthenticationPrincipal User student,
                                            @PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (courseEnrollmentRepository.existsByCourseIdAndStudentId(id, student.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already enrolled"));
        }
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .student(student)
                .build();
        courseEnrollmentRepository.save(enrollment);
        return ResponseEntity.ok(Map.of("message", "Enrolled successfully"));
    }

    @DeleteMapping("/courses/{id}/enroll")
    public ResponseEntity<?> unenrollFromCourse(@AuthenticationPrincipal User student,
                                                @PathVariable Long id) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findByCourseIdAndStudentId(id, student.getId())
                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));
        courseEnrollmentRepository.delete(enrollment);
        return ResponseEntity.ok(Map.of("message", "Unenrolled successfully"));
    }

    // ==================== Response Mappers ====================

    private Map<String, Object> mapExamQuestionForStudent(ExamQuestion eq) {
        Map<String, Object> qmap = new LinkedHashMap<>();
        qmap.put("examQuestionId", eq.getId());
        qmap.put("questionOrder", eq.getQuestionOrder());
        qmap.put("questionNumber", eq.getQuestion().getQuestionNumber());
        qmap.put("questionText", eq.getQuestion().getQuestionText());
        qmap.put("options", eq.getQuestion().getOptions());
        qmap.put("imageUrl", eq.getQuestion().getImageUrl());
        qmap.put("hasSharedReference", eq.getQuestion().isHasSharedReference());
        qmap.put("sharedReferenceId", eq.getQuestion().getSharedReferenceId());
        qmap.put("hasVisualOptions", eq.getQuestion().isHasVisualOptions());
        return qmap;
    }

    private Map<String, Object> mapExamForStudent(Exam exam) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exam.getId());
        map.put("title", exam.getTitle());
        map.put("description", exam.getDescription());
        map.put("teacherName", exam.getTeacher().getUsername());
        map.put("durationMinutes", exam.getDurationMinutes());
        map.put("questionCount", exam.getQuestionCount() != null
                ? exam.getQuestionCount() : exam.getExamQuestions().size());
        map.put("status", exam.getStatus().name());
        map.put("startDatetime", exam.getStartDatetime());
        map.put("endDatetime", exam.getEndDatetime());
        map.put("createdAt", exam.getCreatedAt());
        if (exam.getCourse() != null) {
            map.put("courseId", exam.getCourse().getId());
            map.put("courseName", exam.getCourse().getName());
        }
        return map;
    }

    private Map<String, Object> mapAttemptSummary(ExamAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attempt.getId());
        map.put("examId", attempt.getExam().getId());
        map.put("examTitle", attempt.getExam().getTitle());
        int score = attempt.getScore() != null ? attempt.getScore() : 0;
        int total = attempt.getTotalQuestions() != null ? attempt.getTotalQuestions() : 0;
        map.put("score", score);
        map.put("totalQuestions", total);
        map.put("percentage", total > 0
                ? Math.round(score * 100.0 / total * 100.0) / 100.0
                : 0);
        map.put("submittedAt", attempt.getSubmittedAt());
        return map;
    }

    private Map<String, Object> mapAttemptResult(ExamAttempt attempt) {
        Map<String, Object> map = mapAttemptSummary(attempt);
        map.put("answers", attempt.getAnswers().stream().map(a -> {
            Map<String, Object> amap = new LinkedHashMap<>();
            amap.put("examQuestionId", a.getExamQuestion().getId());
            amap.put("questionText", a.getExamQuestion().getQuestion().getQuestionText());
            amap.put("options", a.getExamQuestion().getQuestion().getOptions());
            amap.put("selectedAnswer", a.getSelectedAnswer());
            amap.put("correctAnswer", a.getExamQuestion().getQuestion().getCorrectAnswer());
            amap.put("correct", a.isCorrect());
            amap.put("explanation", a.getExamQuestion().getQuestion().getAnswerExplanation());
            amap.put("imageUrl", a.getExamQuestion().getQuestion().getImageUrl());
            amap.put("lessonName", a.getExamQuestion().getQuestion().getLessonName());
            return amap;
        }).toList());
        return map;
    }

    private Map<String, Object> mapFailureRate(StudentFailureRate rate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lessonName", rate.getLessonName());
        map.put("badScore", rate.getBadScore());
        map.put("totalWrongCount", rate.getTotalWrongCount());
        map.put("updatedAt", rate.getUpdatedAt());
        return map;
    }
}
