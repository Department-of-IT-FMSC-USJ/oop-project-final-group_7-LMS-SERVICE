package com.example.exam.service;

import com.example.exam.dto.CreateExamRequest;
import com.example.exam.dto.PublishExamRequest;
import com.example.exam.dto.SubmitExamRequest;
import com.example.exam.entity.*;
import com.example.exam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSetRepository questionSetRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final StudentFailureRateService failureRateService;
    private final NotificationService notificationService;
    private final CourseRepository courseRepository;

    /**
     * Original create exam (select individual questions). Kept for backward compatibility.
     */
    @Transactional
    public Exam createExam(User teacher, CreateExamRequest request) {
        Exam exam = Exam.builder()
                .title(request.title())
                .description(request.description())
                .teacher(teacher)
                .durationMinutes(request.durationMinutes())
                .status(ExamStatus.DRAFT)
                .build();
        examRepository.save(exam);

        List<Question> questions = questionRepository.findByIdIn(request.questionIds());
        if (questions.size() != request.questionIds().size()) {
            throw new RuntimeException("Some questions were not found");
        }

        for (Question q : questions) {
            if (!q.getQuestionSet().getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Question " + q.getId() + " does not belong to you");
            }
        }

        List<ExamQuestion> examQuestions = new ArrayList<>();
        for (int i = 0; i < request.questionIds().size(); i++) {
            Long qId = request.questionIds().get(i);
            Question q = questions.stream()
                    .filter(x -> x.getId().equals(qId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Question not found"));

            ExamQuestion eq = ExamQuestion.builder()
                    .exam(exam)
                    .question(q)
                    .questionOrder(i + 1)
                    .build();
            examQuestions.add(eq);
        }
        examQuestionRepository.saveAll(examQuestions);
        exam.setExamQuestions(examQuestions);
        return exam;
    }

    /**
     * FR-011: Publish exam by selecting papers and/or individual questions + question count.
     * Supports: questionSetIds (whole papers), questionIds (individual questions), or both.
     * The questionCount determines how many are randomly picked per student.
     */
    @Transactional
    public Exam publishExam(User teacher, PublishExamRequest request) {
        List<Question> poolQuestions = new ArrayList<>();

        // Collect questions from selected papers
        if (request.questionSetIds() != null && !request.questionSetIds().isEmpty()) {
            List<QuestionSet> sets = questionSetRepository.findAllById(request.questionSetIds());
            if (sets.size() != request.questionSetIds().size()) {
                throw new RuntimeException("Some question sets were not found");
            }
            for (QuestionSet qs : sets) {
                if (!qs.getTeacher().getId().equals(teacher.getId())) {
                    throw new RuntimeException("Question set " + qs.getId() + " does not belong to you");
                }
            }
            poolQuestions.addAll(questionRepository.findByQuestionSetIdIn(request.questionSetIds()));
        }

        // Collect individually selected questions
        if (request.questionIds() != null && !request.questionIds().isEmpty()) {
            List<Question> individual = questionRepository.findByIdIn(request.questionIds());
            if (individual.size() != request.questionIds().size()) {
                throw new RuntimeException("Some questions were not found");
            }
            for (Question q : individual) {
                if (!q.getQuestionSet().getTeacher().getId().equals(teacher.getId())) {
                    throw new RuntimeException("Question " + q.getId() + " does not belong to you");
                }
            }
            // Avoid duplicates if a question was already included via its paper
            Set<Long> existingIds = poolQuestions.stream().map(Question::getId).collect(Collectors.toSet());
            for (Question q : individual) {
                if (!existingIds.contains(q.getId())) {
                    poolQuestions.add(q);
                }
            }
        }

        if (poolQuestions.isEmpty()) {
            throw new RuntimeException("No questions selected — provide questionSetIds and/or questionIds");
        }
        if (request.questionCount() > poolQuestions.size()) {
            throw new RuntimeException("Question count (" + request.questionCount()
                    + ") exceeds available questions (" + poolQuestions.size() + ")");
        }

        LocalDateTime startDt = request.startDatetime() != null
                ? LocalDateTime.parse(request.startDatetime()) : null;
        LocalDateTime endDt = request.endDatetime() != null
                ? LocalDateTime.parse(request.endDatetime()) : null;

        Exam exam = Exam.builder()
                .title(request.title())
                .description(request.description())
                .teacher(teacher)
                .durationMinutes(request.durationMinutes())
                .questionCount(request.questionCount())
                .status(ExamStatus.PUBLISHED)
                .startDatetime(startDt)
                .endDatetime(endDt)
                .build();

        // Link exam to course if provided
        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new RuntimeException("Course not found"));
            if (!course.getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Course does not belong to you");
            }
            exam.setCourse(course);
        }

        examRepository.save(exam);

        // Store all pool questions as ExamQuestion (the full pool)
        List<ExamQuestion> examQuestions = new ArrayList<>();
        for (int i = 0; i < poolQuestions.size(); i++) {
            ExamQuestion eq = ExamQuestion.builder()
                    .exam(exam)
                    .question(poolQuestions.get(i))
                    .questionOrder(i + 1)
                    .build();
            examQuestions.add(eq);
        }
        examQuestionRepository.saveAll(examQuestions);
        exam.setExamQuestions(examQuestions);
        return exam;
    }

    /**
     * FR-010: Preview exam - simulate random question selection for teacher.
     */
    @Transactional(readOnly = true)
    public List<ExamQuestion> previewExam(Long examId) {
        Exam exam = getExamById(examId);
        List<ExamQuestion> pool = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);

        if (exam.getQuestionCount() != null && exam.getQuestionCount() < pool.size()) {
            Collections.shuffle(pool);
            return pool.subList(0, exam.getQuestionCount());
        }
        return pool;
    }

    /**
     * FR-013 + FR-014: Start exam with random + adaptive question selection.
     * Persists the selected questions so the same set is returned on reload.
     */
    @Transactional
    public ExamAttempt startExam(User student, Long examId) {
        Exam exam = getExamById(examId);

        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new RuntimeException("Exam is not published");
        }

        // Check scheduling
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartDatetime() != null && now.isBefore(exam.getStartDatetime())) {
            throw new RuntimeException("Exam has not started yet");
        }
        if (exam.getEndDatetime() != null && now.isAfter(exam.getEndDatetime())) {
            throw new RuntimeException("Exam has ended");
        }

        // Get full question pool for this exam
        List<ExamQuestion> pool = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);
        List<ExamQuestion> selected;

        if (exam.getQuestionCount() != null && exam.getQuestionCount() < pool.size()) {
            selected = selectQuestionsAdaptive(student, pool, exam.getQuestionCount());
        } else {
            selected = new ArrayList<>(pool);
            Collections.shuffle(selected);
        }

        // Persist selected question IDs so the same set is returned on reload
        String selectedIds = selected.stream()
                .map(eq -> eq.getId().toString())
                .collect(Collectors.joining(","));

        // Create attempt
        ExamAttempt attempt = ExamAttempt.builder()
                .exam(exam)
                .student(student)
                .totalQuestions(selected.size())
                .selectedExamQuestionIds(selectedIds)
                .build();
        examAttemptRepository.save(attempt);

        return attempt;
    }

    /**
     * Get the persisted selected questions for an active attempt.
     */
    @Transactional(readOnly = true)
    public List<ExamQuestion> getQuestionsForAttempt(ExamAttempt attempt) {
        if (attempt.getSelectedExamQuestionIds() == null || attempt.getSelectedExamQuestionIds().isBlank()) {
            // Fallback: return all exam questions
            return examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(attempt.getExam().getId());
        }
        List<Long> ids = Arrays.stream(attempt.getSelectedExamQuestionIds().split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
        List<ExamQuestion> all = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(attempt.getExam().getId());
        Set<Long> idSet = new HashSet<>(ids);
        return all.stream().filter(eq -> idSet.contains(eq.getId())).toList();
    }

    /**
     * FR-014: Adaptive question picking - prioritize weak lessons then fill randomly.
     */
    private List<ExamQuestion> selectQuestionsAdaptive(User student, List<ExamQuestion> pool, int count) {
        // Get student's weak lessons
        List<StudentFailureRate> weakLessons = failureRateService.getWeakLessons(student.getId());
        Set<String> weakLessonNames = weakLessons.stream()
                .map(StudentFailureRate::getLessonName)
                .collect(Collectors.toSet());

        List<ExamQuestion> weakQuestions = new ArrayList<>();
        List<ExamQuestion> normalQuestions = new ArrayList<>();

        for (ExamQuestion eq : pool) {
            String lesson = eq.getQuestion().getLessonName();
            if (lesson != null && weakLessonNames.contains(lesson)) {
                weakQuestions.add(eq);
            } else {
                normalQuestions.add(eq);
            }
        }

        Collections.shuffle(weakQuestions);
        Collections.shuffle(normalQuestions);

        List<ExamQuestion> selected = new ArrayList<>();

        // Take weak-lesson questions first
        for (ExamQuestion eq : weakQuestions) {
            if (selected.size() >= count) break;
            selected.add(eq);
        }

        // Fill remaining with normal random questions
        for (ExamQuestion eq : normalQuestions) {
            if (selected.size() >= count) break;
            selected.add(eq);
        }

        // Shuffle final selection so weak questions aren't all at the start
        Collections.shuffle(selected);
        return selected;
    }

    /**
     * FR-015 + FR-016: Submit exam with auto-evaluation and failure rate tracking.
     */
    @Transactional
    public ExamAttempt submitExam(User student, Long examId, SubmitExamRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);

        ExamAttempt attempt = ExamAttempt.builder()
                .exam(exam)
                .student(student)
                .totalQuestions(request.answers().size())
                .submittedAt(LocalDateTime.now())
                .build();
        examAttemptRepository.save(attempt);

        int score = 0;
        List<StudentAnswer> answers = new ArrayList<>();

        for (SubmitExamRequest.AnswerSubmission sub : request.answers()) {
            ExamQuestion eq = examQuestions.stream()
                    .filter(x -> x.getId().equals(sub.examQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Exam question not found: " + sub.examQuestionId()));

            boolean isCorrect = eq.getQuestion().getCorrectAnswer()
                    .trim().equalsIgnoreCase(sub.selectedAnswer().trim());

            if (isCorrect) {
                score++;
                // FR-014: Decrement bad score if weak-lesson question answered correctly
                failureRateService.decrementBadScore(student, eq.getQuestion().getLessonName());
            } else {
                // FR-016: Increment failure rate for incorrect answers
                failureRateService.incrementBadScore(student, eq.getQuestion().getLessonName());
            }

            StudentAnswer answer = StudentAnswer.builder()
                    .attempt(attempt)
                    .examQuestion(eq)
                    .selectedAnswer(sub.selectedAnswer())
                    .correct(isCorrect)
                    .build();
            answers.add(answer);
        }

        studentAnswerRepository.saveAll(answers);
        attempt.setScore(score);
        attempt.setAnswers(answers);
        examAttemptRepository.save(attempt);

        // Notify teacher that a student completed their exam
        try {
            notificationService.sendNotification(
                    null,
                    exam.getTeacher().getId(),
                    NotificationType.STUDENT_COMPLETED,
                    "Student completed exam",
                    student.getUsername() + " completed \"" + exam.getTitle()
                            + "\" with score " + score + "/" + request.answers().size()
            );
        } catch (Exception ignored) {
            // Don't fail submission if notification fails
        }

        return attempt;
    }

    @Transactional
    public void publishExamStatus(Long examId, Long teacherId) {
        Exam exam = getExamById(examId);
        if (!exam.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized");
        }
        exam.setStatus(ExamStatus.PUBLISHED);
        examRepository.save(exam);
    }

    @Transactional
    public void closeExam(Long examId, Long teacherId) {
        Exam exam = getExamById(examId);
        if (!exam.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized");
        }
        exam.setStatus(ExamStatus.CLOSED);
        examRepository.save(exam);
    }

    @Transactional(readOnly = true)
    public List<Exam> getTeacherExams(Long teacherId) {
        return examRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public List<Exam> getAllPublishedExams() {
        return examRepository.findByStatusOrderByCreatedAtDesc(ExamStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<Exam> getPublishedExamsByCourseIds(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return List.of();
        return examRepository.findByCourseIdIn(courseIds).stream()
                .filter(e -> e.getStatus() == ExamStatus.PUBLISHED)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Exam> getAllExams() {
        return examRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Exam getExamById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
    }

    @Transactional
    public void deleteExam(Long id, Long teacherId) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        if (!exam.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized to delete this exam");
        }
        examRepository.delete(exam);
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getStudentAttempts(Long studentId) {
        return examAttemptRepository.findByStudentIdOrderByStartedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public ExamAttempt getAttemptById(Long id) {
        return examAttemptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attempt not found"));
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getExamAttempts(Long examId) {
        return examAttemptRepository.findByExamId(examId);
    }
}
