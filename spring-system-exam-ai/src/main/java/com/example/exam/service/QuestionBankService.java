package com.example.exam.service;

import com.example.exam.dto.AddQuestionRequest;
import com.example.exam.dto.ExamAiResponse;
import com.example.exam.entity.Question;
import com.example.exam.entity.QuestionSet;
import com.example.exam.entity.User;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final ExamAiClient examAiClient;

    /**
     * Submit PDF for async AI extraction — returns job info for polling.
     */
    public Map<String, Object> submitPdfForExtraction(MultipartFile pdfFile, String geminiApiKey) {
        return examAiClient.submitPdf(pdfFile, geminiApiKey);
    }

    /**
     * Poll job status from exam-ai service.
     */
    public Map<String, Object> getJobStatus(String jobId) {
        return examAiClient.getJobStatus(jobId);
    }

    @Transactional
    public QuestionSet importFromJson(User teacher, String setName, ExamAiResponse response) {
        if (!response.success() || response.questions() == null) {
            throw new RuntimeException("Invalid question data");
        }
        return saveQuestionSet(teacher, setName, null, response);
    }

    private QuestionSet saveQuestionSet(User teacher, String setName, String subject, ExamAiResponse response) {
        QuestionSet questionSet = QuestionSet.builder()
                .name(setName)
                .subject(subject)
                .teacher(teacher)
                .build();
        questionSetRepository.save(questionSet);

        List<Question> questions = new ArrayList<>();
        for (ExamAiResponse.ExamAiQuestion q : response.questions()) {
            Question question = Question.builder()
                    .questionSet(questionSet)
                    .questionNumber(q.questionNumber())
                    .pageNumber(q.pageNumber())
                    .questionText(q.questionText())
                    .options(new ArrayList<>(q.options()))
                    .correctAnswer(q.correctAnswer())
                    .answerExplanation(q.answerExplanation())
                    .hasSharedReference(q.hasSharedReference())
                    .sharedReferenceId(q.sharedReferenceId())
                    .imageUrl(q.imageUrl())
                    .hasVisualOptions(q.hasVisualOptions())
                    .build();
            questions.add(question);
        }
        questionRepository.saveAll(questions);
        questionSet.setQuestions(questions);
        return questionSet;
    }

    /**
     * FR-005: Create empty paper/question set.
     */
    @Transactional
    public QuestionSet createPaper(User teacher, String name, String subject, String description) {
        QuestionSet questionSet = QuestionSet.builder()
                .name(name)
                .subject(subject)
                .teacher(teacher)
                .build();
        return questionSetRepository.save(questionSet);
    }

    /**
     * FR-006: Add a question manually to a paper.
     */
    @Transactional
    public Question addQuestion(User teacher, Long questionSetId, AddQuestionRequest request) {
        QuestionSet set = getSetById(questionSetId);
        if (!set.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Not authorized");
        }

        int nextNumber = set.getQuestions().size() + 1;

        Question question = Question.builder()
                .questionSet(set)
                .questionNumber(String.valueOf(nextNumber))
                .questionText(request.questionText())
                .options(List.of(request.optionA(), request.optionB(), request.optionC(), request.optionD()))
                .correctAnswer(request.correctAnswer())
                .answerExplanation(request.answerExplanation())
                .imageUrl(request.imageUrl())
                .lessonName(request.lessonName())
                .marks(request.marks() != null ? request.marks() : 1.0)
                .build();

        return questionRepository.save(question);
    }

    /**
     * FR-006: Edit an existing question.
     */
    @Transactional
    public Question editQuestion(User teacher, Long questionId, AddQuestionRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        if (!question.getQuestionSet().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Not authorized");
        }

        question.setQuestionText(request.questionText());
        question.setOptions(List.of(request.optionA(), request.optionB(), request.optionC(), request.optionD()));
        question.setCorrectAnswer(request.correctAnswer());
        question.setAnswerExplanation(request.answerExplanation());
        question.setImageUrl(request.imageUrl());
        question.setLessonName(request.lessonName());
        if (request.marks() != null) question.setMarks(request.marks());

        return questionRepository.save(question);
    }

    /**
     * Delete a single question.
     */
    @Transactional
    public void deleteQuestion(User teacher, Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        if (!question.getQuestionSet().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Not authorized");
        }

        questionRepository.delete(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionSet> getTeacherSets(Long teacherId) {
        return questionSetRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public QuestionSet getSetById(Long id) {
        return questionSetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question set not found"));
    }

    @Transactional
    public void deleteSet(Long id, Long teacherId) {
        QuestionSet set = questionSetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question set not found"));
        if (!set.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized to delete this set");
        }
        questionSetRepository.delete(set);
    }
}
