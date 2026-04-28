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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExamAttemptService — FR-5.2 (Submission) + FR-5.3 (Auto-grading)
 *
 * 1. Start attempt (eligibility check, question set generation)
 * 2. Auto-save answers every 30 seconds + anti-cheat event logging
 * 3. Submit + auto-grade MCQ/ShortAnswer
 * 4. Return results (if released)
 */
@Service
@Transactional
public class ExamAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(ExamAttemptService.class);

    @Autowired private ExamRepository               examRepository;
    @Autowired private ExamAttemptRepository        attemptRepository;
    @Autowired private AttemptAnswerRepository      answerRepository;
    @Autowired private AttemptQuestionGradeRepository gradeRepository;
    @Autowired private ExamQuestionRepository       questionRepository;
    @Autowired private ExamPoolConfigRepository     poolConfigRepository;
    @Autowired private EnrollmentRepository         enrollmentRepository;
    @Autowired private ExamService                  examService;

    @PersistenceContext
    private EntityManager em;

    // =====================================================
    // START ATTEMPT — FR-5.2 Steps 127–130
    // =====================================================

    public AttemptResponse startAttempt(Long examId, User student) {
        Exam exam = examService.findExamById(examId);

        // Must be enrolled
        if (!enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                student.getId(), exam.getCourse().getId(), Enrollment.EnrollmentStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not enrolled in this course.");
        }

        // Exam must be ACTIVE
        if (exam.getStatus() != Exam.ExamStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This exam is not currently available.");
        }

        // Availability window check
        LocalDateTime now = LocalDateTime.now();
        if (exam.getAvailableFrom() != null && now.isBefore(exam.getAvailableFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This exam has not opened yet. It opens on " + exam.getAvailableFrom() + ".");
        }
        if (exam.getAvailableUntil() != null && now.isAfter(exam.getAvailableUntil())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This exam has closed. No new attempts are accepted.");
        }

        // Check for existing IN_PROGRESS attempt (resume)
        Optional<ExamAttempt> existing = attemptRepository.findByExamIdAndStudentIdAndStatus(
            examId, student.getId(), ExamAttempt.AttemptStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            ExamAttempt att = existing.get();
            if (att.isExpired()) {
                // Auto-submit the expired attempt
                autoSubmitExpired(att);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Your previous attempt expired and has been automatically submitted.");
            }
            logger.info("Student {} resuming attempt {} for exam {}", student.getEmail(), att.getId(), examId);
            return buildAttemptResponse(att, true);
        }

        // Max attempts check
        if (exam.getMaxAttempts() != null) {
            long completed = attemptRepository.countCompletedAttempts(examId, student.getId());
            if (completed >= exam.getMaxAttempts()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You have used all your attempts for this exam.");
            }
        }

        // Create new attempt
        LocalDateTime expiresAt = exam.getTimeLimitMinutes() != null
            ? now.plusMinutes(exam.getTimeLimitMinutes()) : null;

        ExamAttempt attempt = ExamAttempt.builder()
            .exam(exam).student(student)
            .status(ExamAttempt.AttemptStatus.IN_PROGRESS)
            .expiresAt(expiresAt)
            .build();
        attempt = attemptRepository.save(attempt);

        // Generate question set (with optional randomization)
        generateQuestionSet(attempt, exam);

        logger.info("New attempt {} started by {} for exam {}", attempt.getId(), student.getEmail(), examId);
        return buildAttemptResponse(attempt, false);
    }

    // =====================================================
    // AUTO-SAVE — FR-5.2 Step 132
    // =====================================================

    public void autoSave(Long attemptId, AutoSaveRequest req, User student) {
        ExamAttempt attempt = findAttemptById(attemptId);
        validateStudentOwnsAttempt(attempt, student);

        if (attempt.getStatus() != ExamAttempt.AttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This attempt is no longer in progress.");
        }

        if (req.getAnswers() != null) {
            for (AutoSaveRequest.AnswerDto dto : req.getAnswers()) {
                Optional<AttemptAnswer> existing = answerRepository
                    .findByAttemptIdAndQuestionId(attemptId, dto.getQuestionId());

                if (existing.isPresent()) {
                    AttemptAnswer ans = existing.get();
                    ans.setAnswerText(dto.getAnswerText());
                    ans.setFlagged(dto.isFlagged());
                    answerRepository.save(ans);
                } else {
                    ExamQuestion question = questionRepository.findById(dto.getQuestionId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question not found."));
                    answerRepository.save(AttemptAnswer.builder()
                        .attempt(attempt)
                        .question(question)
                        .answerText(dto.getAnswerText())
                        .flagged(dto.isFlagged())
                        .build());
                }
            }
        }

        // Log anti-cheat events (FR-5.2: logged only, not blocked)
        if (req.getEventType() != null && !req.getEventType().isBlank()) {
            em.createNativeQuery(
                "INSERT INTO attempt_events (attempt_id, event_type) VALUES (?, ?)")
                .setParameter(1, attemptId)
                .setParameter(2, req.getEventType())
                .executeUpdate();
        }
    }

    // =====================================================
    // SUBMIT — FR-5.2 Steps 133–137
    // =====================================================

    public AttemptResultResponse submitAttempt(Long attemptId, User student) {
        ExamAttempt attempt = findAttemptById(attemptId);
        validateStudentOwnsAttempt(attempt, student);

        if (attempt.getStatus() != ExamAttempt.AttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This attempt has already been submitted.");
        }

        attempt.setStatus(ExamAttempt.AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt = attemptRepository.save(attempt);

        // Auto-grade objective questions (FR-5.3)
        autoGrade(attempt);

        // Check if all questions are now graded (no manual pending)
        long pending = gradeRepository.countByAttemptIdAndGradeStatus(
            attemptId, AttemptQuestionGrade.GradeStatus.MANUAL_PENDING);

        if (pending == 0) {
            // All auto-graded — finalize
            finalizeGrade(attempt);
        }

        logger.info("Attempt {} submitted by {}", attemptId, student.getEmail());
        return buildResultResponse(attempt);
    }

    // =====================================================
    // AUTO-SUBMIT EXPIRED ATTEMPT — FR-5.2
    // =====================================================

    public void autoSubmitExpired(ExamAttempt attempt) {
        attempt.setStatus(ExamAttempt.AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt = attemptRepository.save(attempt);
        autoGrade(attempt);

        long pending = gradeRepository.countByAttemptIdAndGradeStatus(
            attempt.getId(), AttemptQuestionGrade.GradeStatus.MANUAL_PENDING);
        if (pending == 0) finalizeGrade(attempt);
        logger.info("Attempt {} auto-submitted (timer expired)", attempt.getId());
    }

    // =====================================================
    // GET RESULTS — FR-5.4
    // =====================================================

    @Transactional(readOnly = true)
    public AttemptResultResponse getResults(Long attemptId, User viewer) {
        ExamAttempt attempt = findAttemptById(attemptId);

        if (viewer.getRole() == User.Role.STUDENT &&
                !attempt.getStudent().getId().equals(viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        if (attempt.getStatus() == ExamAttempt.AttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This exam is still in progress.");
        }

        return buildResultResponse(attempt);
    }

    // =====================================================
    // PRIVATE: Generate Question Set (randomization)
    // FR-5.2 Step 130
    // =====================================================

    private void generateQuestionSet(ExamAttempt attempt, Exam exam) {
        List<ExamQuestion> selected;

        if (exam.isUseRandomization()) {
            selected = new ArrayList<>();
            List<ExamPoolConfig> pools = poolConfigRepository.findByExamId(exam.getId());
            Random rng = new Random();

            for (ExamPoolConfig pool : pools) {
                List<ExamQuestion> poolQ = questionRepository
                    .findByExamIdAndPoolName(exam.getId(), pool.getPoolName());
                Collections.shuffle(poolQ, rng);
                selected.addAll(poolQ.subList(0, Math.min(pool.getDrawCount(), poolQ.size())));
            }
            // Include questions NOT in any pool (always include)
            List<ExamQuestion> unPooled = questionRepository.findByExamIdOrderBySortOrderAsc(exam.getId())
                .stream().filter(q -> q.getPoolName() == null).collect(Collectors.toList());
            selected.addAll(unPooled);
        } else {
            selected = questionRepository.findByExamIdOrderBySortOrderAsc(exam.getId());
        }

        // Shuffle the final list
        Collections.shuffle(selected);

        int order = 0;
        for (ExamQuestion q : selected) {
            AttemptQuestion aq = AttemptQuestion.builder()
                .attempt(attempt).question(q).displayOrder(order++)
                .build();
            em.persist(aq);
        }
        em.flush();
    }

    // =====================================================
    // PRIVATE: Auto-grade — FR-5.3 Steps 138–141
    // =====================================================

    private void autoGrade(ExamAttempt attempt) {
        List<AttemptQuestion> qs = attempt.getAttemptQuestions();
        // If lazy loaded, reload
        if (qs == null || qs.isEmpty()) {
            qs = em.createQuery(
                "SELECT aq FROM AttemptQuestion aq WHERE aq.attempt.id = :id ORDER BY aq.displayOrder",
                AttemptQuestion.class)
                .setParameter("id", attempt.getId())
                .getResultList();
        }

        for (AttemptQuestion aq : qs) {
            ExamQuestion q = aq.getQuestion();
            Optional<AttemptAnswer> ansOpt = answerRepository
                .findByAttemptIdAndQuestionId(attempt.getId(), q.getId());

            String answer = ansOpt.map(AttemptAnswer::getAnswerText).orElse(null);

            AttemptQuestionGrade.GradeStatus gradeStatus;
            BigDecimal scoreAwarded = BigDecimal.ZERO;

            if (q.getQuestionType() == ExamQuestion.QuestionType.MCQ) {
                // Compare submitted option ID(s) against correct option
                gradeStatus = gradeMcq(q, answer) ? AttemptQuestionGrade.GradeStatus.CORRECT
                                                   : AttemptQuestionGrade.GradeStatus.INCORRECT;
                scoreAwarded = gradeStatus == AttemptQuestionGrade.GradeStatus.CORRECT ? q.getPoints() : BigDecimal.ZERO;

            } else if (q.getQuestionType() == ExamQuestion.QuestionType.SHORT_ANSWER) {
                if (q.getShortAnswerMethod() == ExamQuestion.ShortAnswerMethod.MANUAL) {
                    gradeStatus = AttemptQuestionGrade.GradeStatus.MANUAL_PENDING;
                } else {
                    boolean correct = gradeShortAnswer(q, answer);
                    gradeStatus = correct ? AttemptQuestionGrade.GradeStatus.CORRECT
                                         : AttemptQuestionGrade.GradeStatus.INCORRECT;
                    scoreAwarded = correct ? q.getPoints() : BigDecimal.ZERO;
                }
            } else {
                // ESSAY / FILE_UPLOAD → manual pending
                gradeStatus = AttemptQuestionGrade.GradeStatus.MANUAL_PENDING;
            }

            AttemptQuestionGrade grade = AttemptQuestionGrade.builder()
                .attempt(attempt).question(q)
                .scoreAwarded(scoreAwarded).maxScore(q.getPoints())
                .gradeStatus(gradeStatus)
                .build();
            gradeRepository.save(grade);
        }
    }

    private boolean gradeMcq(ExamQuestion q, String answer) {
        if (answer == null || answer.isBlank()) return false;
        // answer = comma-separated option IDs selected by student
        Set<Long> selected = Arrays.stream(answer.split(","))
            .map(s -> {
                try { return Long.parseLong(s.trim()); }
                catch (NumberFormatException e) { return -1L; }
            })
            .filter(id -> id > 0)
            .collect(Collectors.toSet());

        Set<Long> correct = q.getOptions().stream()
            .filter(ExamQuestionOption::isCorrect)
            .map(ExamQuestionOption::getId)
            .collect(Collectors.toSet());

        return selected.equals(correct);
    }

    private boolean gradeShortAnswer(ExamQuestion q, String answer) {
        if (answer == null || answer.isBlank() || q.getExpectedAnswer() == null) return false;
        String norm = answer.trim().toLowerCase();
        String expected = q.getExpectedAnswer().trim().toLowerCase();

        if (q.getShortAnswerMethod() == ExamQuestion.ShortAnswerMethod.EXACT_MATCH) {
            return norm.equals(expected);
        } else { // KEYWORD_MATCH
            // expected is comma-separated keywords
            return Arrays.stream(expected.split(","))
                .map(String::trim)
                .anyMatch(norm::contains);
        }
    }

    // =====================================================
    // PRIVATE: Finalize grade (compute total) — FR-5.3
    // =====================================================

    void finalizeGrade(ExamAttempt attempt) {
        List<AttemptQuestionGrade> grades = gradeRepository.findByAttemptId(attempt.getId());

        BigDecimal total = grades.stream()
            .map(AttemptQuestionGrade::getScoreAwarded)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxMarks = attempt.getExam().getTotalMarks();
        BigDecimal pct = maxMarks.compareTo(BigDecimal.ZERO) > 0
            ? total.divide(maxMarks, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                   .setScale(1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        boolean passed = pct.compareTo(attempt.getExam().getPassingGradePct()) >= 0;

        attempt.setTotalScore(total);
        attempt.setPercentage(pct);
        attempt.setPassed(passed);
        attempt.setStatus(ExamAttempt.AttemptStatus.GRADED);
        attemptRepository.save(attempt);

        // If result release = IMMEDIATE, auto-release
        if (attempt.getExam().getResultRelease() == Exam.ResultRelease.IMMEDIATE &&
                !attempt.getExam().isResultsReleased()) {
            attempt.getExam().setResultsReleased(true);
            examRepository.save(attempt.getExam());
        }

        logger.info("Attempt {} finalized: score={}, passed={}", attempt.getId(), total, passed);
    }

    // =====================================================
    // PRIVATE: Build response objects
    // =====================================================

    private AttemptResponse buildAttemptResponse(ExamAttempt attempt, boolean isResume) {
        List<AttemptQuestion> qs = em.createQuery(
            "SELECT aq FROM AttemptQuestion aq JOIN FETCH aq.question q " +
            "LEFT JOIN FETCH q.options WHERE aq.attempt.id = :id ORDER BY aq.displayOrder",
            AttemptQuestion.class)
            .setParameter("id", attempt.getId())
            .getResultList();

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        Map<Long, AttemptAnswer> ansMap = answers.stream()
            .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

        List<AttemptResponse.AttemptQuestionDto> qDtos = qs.stream().map(aq -> {
            ExamQuestion q = aq.getQuestion();
            AttemptAnswer saved = ansMap.get(q.getId());

            // Build options WITHOUT is_correct field
            List<AttemptResponse.OptionDto> opts = null;
            if (q.getQuestionType() == ExamQuestion.QuestionType.MCQ) {
                opts = q.getOptions().stream()
                    .map(o -> AttemptResponse.OptionDto.builder()
                        .optionId(o.getId()).optionText(o.getOptionText()).build())
                    .collect(Collectors.toList());
            }

            return AttemptResponse.AttemptQuestionDto.builder()
                .questionId(q.getId())
                .displayOrder(aq.getDisplayOrder())
                .questionType(q.getQuestionType().name())
                .questionText(q.getQuestionText())
                .points(q.getPoints())
                .options(opts)
                .allowedFileTypes(q.getAllowedFileTypes())
                .maxFileSizeMb(q.getMaxFileSizeMb())
                .savedAnswer(saved != null ? saved.getAnswerText() : null)
                .flagged(saved != null && saved.isFlagged())
                .build();
        }).collect(Collectors.toList());

        long answered = answerRepository.countAnswered(attempt.getId());

        return AttemptResponse.builder()
            .attemptId(attempt.getId())
            .examId(attempt.getExam().getId())
            .examTitle(attempt.getExam().getTitle())
            .timeLimitMinutes(attempt.getExam().getTimeLimitMinutes())
            .startedAt(attempt.getStartedAt())
            .expiresAt(attempt.getExpiresAt())
            .status(attempt.getStatus().name())
            .totalQuestions(qs.size())
            .answeredCount(answered)
            .questions(qDtos)
            .build();
    }

    AttemptResultResponse buildResultResponse(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        boolean released = exam.isResultsReleased();

        List<AttemptQuestion> qs = em.createQuery(
            "SELECT aq FROM AttemptQuestion aq JOIN FETCH aq.question q " +
            "WHERE aq.attempt.id = :id ORDER BY aq.displayOrder",
            AttemptQuestion.class)
            .setParameter("id", attempt.getId()).getResultList();

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        Map<Long, String> ansMap = answers.stream()
            .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a.getAnswerText() != null ? a.getAnswerText() : ""));

        List<AttemptQuestionGrade> grades = gradeRepository.findByAttemptId(attempt.getId());
        Map<Long, AttemptQuestionGrade> gradeMap = grades.stream()
            .collect(Collectors.toMap(g -> g.getQuestion().getId(), g -> g));

        List<AttemptResultResponse.QuestionResult> results = qs.stream().map(aq -> {
            ExamQuestion q = aq.getQuestion();
            AttemptQuestionGrade grade = gradeMap.get(q.getId());
            String correctAnswer = null;
            if (released && q.getQuestionType() == ExamQuestion.QuestionType.MCQ) {
                correctAnswer = q.getOptions().stream()
                    .filter(ExamQuestionOption::isCorrect)
                    .map(ExamQuestionOption::getOptionText)
                    .collect(Collectors.joining(", "));
            }
            return AttemptResultResponse.QuestionResult.builder()
                .questionId(q.getId())
                .displayOrder(aq.getDisplayOrder())
                .questionType(q.getQuestionType().name())
                .questionText(q.getQuestionText())
                .pointsEarned(grade != null ? grade.getScoreAwarded() : null)
                .maxPoints(q.getPoints())
                .studentAnswer(ansMap.getOrDefault(q.getId(), ""))
                .correctAnswer(correctAnswer)
                .gradeStatus(grade != null ? grade.getGradeStatus().name() : "MANUAL_PENDING")
                .feedback(released && grade != null ? grade.getFeedback() : null)
                .build();
        }).collect(Collectors.toList());

        return AttemptResultResponse.builder()
            .attemptId(attempt.getId())
            .examId(exam.getId())
            .examTitle(exam.getTitle())
            .courseTitle(exam.getCourse().getTitle())
            .totalScore(attempt.getTotalScore())
            .totalMarks(exam.getTotalMarks())
            .percentage(attempt.getPercentage())
            .passed(Boolean.TRUE.equals(attempt.getPassed()))
            .status(attempt.getStatus().name())
            .resultsReleased(released)
            .submittedAt(attempt.getSubmittedAt())
            .startedAt(attempt.getStartedAt())
            .questionResults(results)
            .build();
    }

    private void validateStudentOwnsAttempt(ExamAttempt attempt, User student) {
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }
    }

    // =====================================================
    // GET MY RESULTS — FR-5.4 (student: all released results)
    // =====================================================

    @Transactional(readOnly = true)
    public List<StudentResultSummaryDto> getMyResults(User student) {
        return attemptRepository.findReleasedResultsForStudent(student.getId())
            .stream()
            .map(a -> {
                Exam exam = a.getExam();
                return StudentResultSummaryDto.builder()
                    .attemptId(a.getId())
                    .examId(exam.getId())
                    .examTitle(exam.getTitle())
                    .courseTitle(exam.getCourse().getTitle())
                    .courseCode(exam.getCourse().getCourseCode())
                    .totalScore(a.getTotalScore())
                    .totalMarks(exam.getTotalMarks())
                    .percentage(a.getPercentage())
                    .passed(Boolean.TRUE.equals(a.getPassed()))
                    .status(a.getStatus().name())
                    .submittedAt(a.getSubmittedAt())
                    .availableFrom(exam.getAvailableFrom())
                    .availableUntil(exam.getAvailableUntil())
                    .timeLimitMinutes(exam.getTimeLimitMinutes())
                    .passingGradePct(exam.getPassingGradePct())
                    .build();
            })
            .collect(Collectors.toList());
    }

    public ExamAttempt findAttemptById(Long id) {
        return attemptRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));
    }

    // Expose examRepository for ExamGradingService
    ExamRepository getExamRepository() { return examRepository; }
}
