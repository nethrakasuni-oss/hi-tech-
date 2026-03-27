package com.hitech.lms.exam.service;
import com.hitech.lms.auth.service.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.user.service.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.service.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.service.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.service.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.service.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.service.*;
import com.hitech.lms.support.repository.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.support.model.*;


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.support.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ExamGradingService — FR-5.3: Manual grading queue and grade submission.
 *
 * Used by Instructors to:
 * 1. View the grading queue for an exam
 * 2. Submit a manual grade for Essay/FileUpload questions
 * 3. Override an auto-graded MCQ/ShortAnswer score
 * 4. Finalize an attempt once all questions are graded
 */
@Service
@Transactional
public class ExamGradingService {

    @Autowired private ExamAttemptRepository          attemptRepository;
    @Autowired private AttemptQuestionGradeRepository  gradeRepository;
    @Autowired private ExamQuestionRepository          questionRepository;
    @Autowired private AttemptAnswerRepository         answerRepository;
    @Autowired private ExamService                     examService;
    @Autowired private ExamAttemptService              attemptService;

    // =====================================================
    // GET GRADING QUEUE — FR-5.3 Steps 142–143
    // =====================================================

    @Transactional(readOnly = true)
    public GradingQueueResponse getGradingQueue(Long examId, User grader) {
        Exam exam = examService.findExamById(examId);
        examService.validateAccessForGrading(grader, exam.getCourse());

        List<ExamAttempt> submissions = attemptRepository.findSubmittedByExamId(examId);
        long pendingCount = submissions.stream()
            .filter(a -> gradeRepository.countByAttemptIdAndGradeStatus(
                a.getId(), AttemptQuestionGrade.GradeStatus.MANUAL_PENDING) > 0)
            .count();
        long gradedCount = submissions.size() - pendingCount;

        List<GradingQueueResponse.SubmissionItem> items = submissions.stream().map(attempt -> {
            List<AttemptQuestionGrade> grades = gradeRepository.findByAttemptId(attempt.getId());
            Map<Long, AttemptQuestionGrade> gradeMap = grades.stream()
                .collect(Collectors.toMap(g -> g.getQuestion().getId(), g -> g));

            List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
            Map<Long, String> ansMap = answers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(),
                    a -> a.getAnswerText() != null ? a.getAnswerText() : ""));

            // Only manual questions for grading
            List<GradingQueueResponse.QuestionToGrade> manualQs =
                questionRepository.findByExamIdOrderBySortOrderAsc(examId).stream()
                .filter(q -> q.getQuestionType() == ExamQuestion.QuestionType.ESSAY ||
                             q.getQuestionType() == ExamQuestion.QuestionType.FILE_UPLOAD ||
                             (q.getQuestionType() == ExamQuestion.QuestionType.SHORT_ANSWER &&
                              q.getShortAnswerMethod() == ExamQuestion.ShortAnswerMethod.MANUAL))
                .map(q -> {
                    AttemptQuestionGrade g = gradeMap.get(q.getId());
                    return GradingQueueResponse.QuestionToGrade.builder()
                        .questionId(q.getId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .rubric(q.getRubric())
                        .maxPoints(q.getPoints())
                        .studentAnswer(ansMap.getOrDefault(q.getId(), ""))
                        .gradeStatus(g != null ? g.getGradeStatus().name() : "MANUAL_PENDING")
                        .awardedScore(g != null ? g.getScoreAwarded() : null)
                        .feedback(g != null ? g.getFeedback() : null)
                        .build();
                }).collect(Collectors.toList());

            long pending = manualQs.stream()
                .filter(q -> "MANUAL_PENDING".equals(q.getGradeStatus())).count();

            BigDecimal currentScore = grades.stream()
                .map(AttemptQuestionGrade::getScoreAwarded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return GradingQueueResponse.SubmissionItem.builder()
                .attemptId(attempt.getId())
                .studentId(attempt.getStudent().getId())
                .studentName(attempt.getStudent().getFullName())
                .studentEmail(attempt.getStudent().getEmail())
                .submittedAt(attempt.getSubmittedAt())
                .attemptStatus(attempt.getStatus().name())
                .pendingQuestions(pending)
                .currentScore(currentScore)
                .questionsToGrade(manualQs)
                .build();
        }).collect(Collectors.toList());

        return GradingQueueResponse.builder()
            .examId(exam.getId())
            .examTitle(exam.getTitle())
            .courseTitle(exam.getCourse().getTitle())
            .totalSubmissions(submissions.size())
            .pendingCount(pendingCount)
            .gradedCount(gradedCount)
            .submissions(items)
            .build();
    }

    // =====================================================
    // SUBMIT GRADE — FR-5.3 Steps 145–147
    // =====================================================

    public void gradeQuestion(Long attemptId, Long questionId, GradeSubmissionRequest req, User grader) {
        ExamAttempt attempt = attemptService.findAttemptById(attemptId);
        examService.validateAccessForGrading(grader, attempt.getExam().getCourse());

        ExamQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found."));

        // Score cannot exceed max points
        if (req.getScore().compareTo(question.getPoints()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Score cannot exceed max points (" + question.getPoints() + ").");
        }
        if (req.getScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score cannot be negative.");
        }

        Optional<AttemptQuestionGrade> existing =
            gradeRepository.findByAttemptIdAndQuestionId(attemptId, questionId);

        if (existing.isPresent()) {
            AttemptQuestionGrade grade = existing.get();
            if (req.isOverride()) {
                // Track override (FR-5.3)
                grade.setOverride(true);
                grade.setOriginalScore(grade.getScoreAwarded());
                grade.setOverrideReason(req.getOverrideReason());
            }
            grade.setScoreAwarded(req.getScore());
            grade.setFeedback(req.getFeedback());
            grade.setGradeStatus(AttemptQuestionGrade.GradeStatus.MANUAL_GRADED);
            grade.setGradedBy(grader);
            grade.setGradedAt(LocalDateTime.now());
            gradeRepository.save(grade);
        } else {
            gradeRepository.save(AttemptQuestionGrade.builder()
                .attempt(attempt).question(question)
                .scoreAwarded(req.getScore())
                .maxScore(question.getPoints())
                .gradeStatus(AttemptQuestionGrade.GradeStatus.MANUAL_GRADED)
                .feedback(req.getFeedback())
                .gradedBy(grader)
                .gradedAt(LocalDateTime.now())
                .build());
        }

        // Check if all questions are now graded
        long stillPending = gradeRepository.countByAttemptIdAndGradeStatus(
            attemptId, AttemptQuestionGrade.GradeStatus.MANUAL_PENDING);

        if (stillPending == 0 && attempt.getStatus() == ExamAttempt.AttemptStatus.SUBMITTED) {
            attemptService.finalizeGrade(attempt);
        }
    }
}
