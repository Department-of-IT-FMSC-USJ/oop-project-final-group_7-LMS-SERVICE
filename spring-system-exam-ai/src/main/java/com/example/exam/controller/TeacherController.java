package com.example.exam.controller;

import com.example.exam.dto.AddQuestionRequest;
import com.example.exam.dto.CreateCourseRequest;
import com.example.exam.dto.CreateExamRequest;
import com.example.exam.dto.PublishExamRequest;
import com.example.exam.entity.*;
import com.example.exam.repository.CourseRepository;
import com.example.exam.repository.CourseEnrollmentRepository;
import com.example.exam.service.ExamService;
import com.example.exam.service.QuestionBankService;
import com.example.exam.service.TeacherAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final QuestionBankService questionBankService;
    private final ExamService examService;
    private final TeacherAnalyticsService analyticsService;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    // ==================== Question Sets (Papers) ====================

    /**
     * FR-005: Create empty paper.
     */
    @PostMapping("/question-sets")
    public ResponseEntity<?> createPaper(@AuthenticationPrincipal User teacher,
                                         @RequestBody Map<String, String> request) {
        QuestionSet set = questionBankService.createPaper(teacher,
                request.get("name"), request.get("subject"), request.get("description"));
        return ResponseEntity.ok(mapQuestionSet(set));
    }

    /**
     * FR-004: Submit PDF for async AI extraction — returns job_id for polling.
     */
    @PostMapping("/question-sets/extract-pdf")
    public ResponseEntity<?> extractFromPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "geminiApiKey", required = false) String geminiApiKey) {
        Map<String, Object> job = questionBankService.submitPdfForExtraction(file, geminiApiKey);
        return ResponseEntity.ok(job);
    }

    /**
     * Poll AI extraction job status.
     */
    @GetMapping("/question-sets/extract-pdf/status/{jobId}")
    public ResponseEntity<?> getExtractionStatus(@PathVariable String jobId) {
        Map<String, Object> status = questionBankService.getJobStatus(jobId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/question-sets")
    public ResponseEntity<?> getQuestionSets(@AuthenticationPrincipal User teacher) {
        List<QuestionSet> sets = questionBankService.getTeacherSets(teacher.getId());
        return ResponseEntity.ok(sets.stream().map(this::mapQuestionSetSummary).toList());
    }

    @GetMapping("/question-sets/{id}")
    public ResponseEntity<?> getQuestionSet(@AuthenticationPrincipal User teacher,
                                            @PathVariable Long id) {
        QuestionSet set = questionBankService.getSetById(id);
        if (!set.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(mapQuestionSet(set));
    }

    @DeleteMapping("/question-sets/{id}")
    public ResponseEntity<?> deleteQuestionSet(@AuthenticationPrincipal User teacher,
                                               @PathVariable Long id) {
        questionBankService.deleteSet(id, teacher.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Questions (FR-006) ====================

    /**
     * FR-006: Add question to a paper.
     */
    @PostMapping("/question-sets/{id}/questions")
    public ResponseEntity<?> addQuestion(@AuthenticationPrincipal User teacher,
                                         @PathVariable Long id,
                                         @RequestBody AddQuestionRequest request) {
        Question question = questionBankService.addQuestion(teacher, id, request);
        return ResponseEntity.ok(mapQuestion(question));
    }

    /**
     * FR-006: Edit a question.
     */
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> editQuestion(@AuthenticationPrincipal User teacher,
                                          @PathVariable Long questionId,
                                          @RequestBody AddQuestionRequest request) {
        Question question = questionBankService.editQuestion(teacher, questionId, request);
        return ResponseEntity.ok(mapQuestion(question));
    }

    /**
     * Delete a question.
     */
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@AuthenticationPrincipal User teacher,
                                            @PathVariable Long questionId) {
        questionBankService.deleteQuestion(teacher, questionId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Exams ====================

    /**
     * Create an exam by selecting question IDs from the question bank (legacy).
     */
    @PostMapping("/exams")
    public ResponseEntity<?> createExam(@AuthenticationPrincipal User teacher,
                                        @RequestBody CreateExamRequest request) {
        Exam exam = examService.createExam(teacher, request);
        return ResponseEntity.ok(mapExam(exam));
    }

    /**
     * FR-011: Publish exam by selecting papers + question count.
     */
    @PostMapping("/exams/publish")
    public ResponseEntity<?> publishExam(@AuthenticationPrincipal User teacher,
                                         @RequestBody PublishExamRequest request) {
        Exam exam = examService.publishExam(teacher, request);
        return ResponseEntity.ok(mapExam(exam));
    }

    /**
     * FR-010: Preview exam (simulates random selection).
     */
    @GetMapping("/exams/{id}/preview")
    public ResponseEntity<?> previewExam(@AuthenticationPrincipal User teacher,
                                         @PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        if (!exam.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        List<ExamQuestion> preview = examService.previewExam(id);
        Map<String, Object> result = mapExamSummary(exam);
        result.put("previewQuestions", preview.stream().map(eq -> {
            Map<String, Object> qmap = mapQuestion(eq.getQuestion());
            qmap.put("examQuestionId", eq.getId());
            qmap.put("questionOrder", eq.getQuestionOrder());
            return qmap;
        }).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Change exam status to PUBLISHED.
     */
    @PutMapping("/exams/{id}/publish")
    public ResponseEntity<?> setExamPublished(@AuthenticationPrincipal User teacher,
                                              @PathVariable Long id) {
        examService.publishExamStatus(id, teacher.getId());
        return ResponseEntity.ok(Map.of("message", "Exam published successfully"));
    }

    /**
     * Change exam status to CLOSED.
     */
    @PutMapping("/exams/{id}/close")
    public ResponseEntity<?> closeExam(@AuthenticationPrincipal User teacher,
                                       @PathVariable Long id) {
        examService.closeExam(id, teacher.getId());
        return ResponseEntity.ok(Map.of("message", "Exam closed successfully"));
    }

    @GetMapping("/exams")
    public ResponseEntity<?> getExams(@AuthenticationPrincipal User teacher) {
        List<Exam> exams = examService.getTeacherExams(teacher.getId());
        return ResponseEntity.ok(exams.stream().map(this::mapExamSummary).toList());
    }

    @GetMapping("/exams/{id}")
    public ResponseEntity<?> getExam(@AuthenticationPrincipal User teacher,
                                     @PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        if (!exam.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        Map<String, Object> result = mapExam(exam);
        List<ExamAttempt> attempts = examService.getExamAttempts(id);
        result.put("totalAttempts", attempts.size());
        result.put("attempts", attempts.stream().map(this::mapAttemptSummary).toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/exams/{id}")
    public ResponseEntity<?> deleteExam(@AuthenticationPrincipal User teacher,
                                        @PathVariable Long id) {
        examService.deleteExam(id, teacher.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Analytics (FR-020) ====================

    /**
     * FR-020: View student performance for an exam.
     */
    @GetMapping("/exams/{id}/analytics")
    public ResponseEntity<?> getExamAnalytics(@AuthenticationPrincipal User teacher,
                                              @PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        if (!exam.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(analyticsService.getExamAnalytics(id));
    }

    /**
     * FR-020: View detailed performance of a specific student in an exam.
     */
    @GetMapping("/exams/{examId}/students/{studentId}")
    public ResponseEntity<?> getStudentPerformance(@AuthenticationPrincipal User teacher,
                                                   @PathVariable Long examId,
                                                   @PathVariable Long studentId) {
        Exam exam = examService.getExamById(examId);
        if (!exam.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(analyticsService.getStudentDetailedPerformance(examId, studentId));
    }

    // ==================== Courses ====================

    @PostMapping("/courses")
    public ResponseEntity<?> createCourse(@AuthenticationPrincipal User teacher,
                                          @RequestBody CreateCourseRequest request) {
        Course course = Course.builder()
                .name(request.name())
                .description(request.description())
                .teacher(teacher)
                .build();
        courseRepository.save(course);
        return ResponseEntity.ok(mapCourse(course));
    }

    @GetMapping("/courses")
    public ResponseEntity<?> getCourses(@AuthenticationPrincipal User teacher) {
        List<Course> courses = courseRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId());
        return ResponseEntity.ok(courses.stream().map(this::mapCourse).toList());
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@AuthenticationPrincipal User teacher,
                                          @PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (!course.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        courseRepository.delete(course);
        return ResponseEntity.noContent().build();
    }

    // ==================== Response Mappers ====================

    private Map<String, Object> mapQuestionSetSummary(QuestionSet set) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", set.getId());
        map.put("name", set.getName());
        map.put("subject", set.getSubject());
        map.put("questionCount", set.getQuestions().size());
        map.put("createdAt", set.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapQuestionSet(QuestionSet set) {
        Map<String, Object> map = mapQuestionSetSummary(set);
        map.put("questions", set.getQuestions().stream().map(this::mapQuestion).toList());
        return map;
    }

    private Map<String, Object> mapQuestion(Question q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", q.getId());
        map.put("questionNumber", q.getQuestionNumber());
        map.put("pageNumber", q.getPageNumber());
        map.put("questionText", q.getQuestionText());
        map.put("options", q.getOptions());
        map.put("correctAnswer", q.getCorrectAnswer());
        map.put("answerExplanation", q.getAnswerExplanation());
        map.put("imageUrl", q.getImageUrl());
        map.put("hasSharedReference", q.isHasSharedReference());
        map.put("sharedReferenceId", q.getSharedReferenceId());
        map.put("hasVisualOptions", q.isHasVisualOptions());
        map.put("lessonName", q.getLessonName());
        map.put("marks", q.getMarks());
        return map;
    }

    private Map<String, Object> mapExamSummary(Exam exam) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exam.getId());
        map.put("title", exam.getTitle());
        map.put("description", exam.getDescription());
        map.put("durationMinutes", exam.getDurationMinutes());
        map.put("questionCount", exam.getQuestionCount());
        map.put("totalPoolQuestions", exam.getExamQuestions().size());
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

    private Map<String, Object> mapExam(Exam exam) {
        Map<String, Object> map = mapExamSummary(exam);
        map.put("questions", exam.getExamQuestions().stream().map(eq -> {
            Map<String, Object> qmap = mapQuestion(eq.getQuestion());
            qmap.put("examQuestionId", eq.getId());
            qmap.put("questionOrder", eq.getQuestionOrder());
            return qmap;
        }).toList());
        return map;
    }

    private Map<String, Object> mapAttemptSummary(ExamAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attempt.getId());
        map.put("studentName", attempt.getStudent().getUsername());
        map.put("score", attempt.getScore());
        map.put("totalQuestions", attempt.getTotalQuestions());
        map.put("submittedAt", attempt.getSubmittedAt());
        return map;
    }

    private Map<String, Object> mapCourse(Course course) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", course.getId());
        map.put("name", course.getName());
        map.put("description", course.getDescription());
        map.put("enrolledCount", courseEnrollmentRepository.countByCourseId(course.getId()));
        map.put("createdAt", course.getCreatedAt());
        return map;
    }
}
