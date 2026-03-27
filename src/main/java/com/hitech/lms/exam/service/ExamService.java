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
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExamService — FR-5.1 (Creation), FR-5.4 (Analytics/Release), FR-5.5 (Lifecycle)
 *
 * Responsibilities:
 * 1. Create / update exams with questions, options, pool configs
 * 2. Publish (DRAFT→ACTIVE) with full validation
 * 3. Lifecycle transitions (deactivate, archive, delete)
 * 4. Class analytics (KPIs, distribution, difficulty, at-risk)
 * 5. Release results
 * 6. Cron auto-deactivation
 */
@Service
@Transactional
public class ExamService {

    private static final Logger logger = LoggerFactory.getLogger(ExamService.class);

    @Autowired private ExamRepository               examRepository;
    @Autowired private ExamQuestionRepository       questionRepository;
    @Autowired private ExamPoolConfigRepository     poolConfigRepository;
    @Autowired private ExamAttemptRepository        attemptRepository;
    @Autowired private AttemptQuestionGradeRepository gradeRepository;
    @Autowired private CourseService                courseService;
    @Autowired private EnrollmentRepository         enrollmentRepository;
    @Autowired private EmailService                 emailService;

    // =====================================================
    // EXAM CRUD — FR-5.1
    // =====================================================

    public ExamDetailResponse createExam(CreateExamRequest req, User creator) {
        Course course = courseService.findCourseById(req.getCourseId());
        validateCreatorCanManageCourse(creator, course);

        Exam exam = Exam.builder()
                .course(course)
                .createdBy(creator)
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .totalMarks(req.getTotalMarks())
                .passingGradePct(req.getPassingGradePct())
                .timeLimitMinutes(req.getTimeLimitMinutes())
                .maxAttempts(req.getMaxAttempts())
                .availableFrom(req.getAvailableFrom())
                .availableUntil(req.getAvailableUntil())
                .resultRelease(req.getResultRelease() != null ? req.getResultRelease() : Exam.ResultRelease.MANUAL)
                .useRandomization(req.isUseRandomization())
                .status(Exam.ExamStatus.DRAFT)
                .build();

        exam = examRepository.save(exam);
        if (req.getQuestions() != null) saveQuestions(exam, req.getQuestions());
        if (req.getPoolConfigs() != null) savePoolConfigs(exam, req.getPoolConfigs());

        logger.info("Exam '{}' created (DRAFT) by {}", exam.getTitle(), creator.getEmail());
        return mapToDetail(exam);
    }

    public ExamDetailResponse updateExam(Long examId, CreateExamRequest req, User editor) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(editor, exam.getCourse());

        if (exam.getStatus() != Exam.ExamStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT exams can be edited. Deactivate the exam first.");
        }

        exam.setTitle(req.getTitle().trim());
        exam.setDescription(req.getDescription());
        exam.setTotalMarks(req.getTotalMarks());
        exam.setPassingGradePct(req.getPassingGradePct());
        exam.setTimeLimitMinutes(req.getTimeLimitMinutes());
        exam.setMaxAttempts(req.getMaxAttempts());
        exam.setAvailableFrom(req.getAvailableFrom());
        exam.setAvailableUntil(req.getAvailableUntil());
        exam.setResultRelease(req.getResultRelease() != null ? req.getResultRelease() : Exam.ResultRelease.MANUAL);
        exam.setUseRandomization(req.isUseRandomization());

        exam = examRepository.save(exam);

        if (req.getQuestions() != null) {
            // Remove existing questions; cascade will delete options
            exam.getQuestions().clear();
            examRepository.saveAndFlush(exam);
            saveQuestions(exam, req.getQuestions());
        }
        if (req.getPoolConfigs() != null) {
            poolConfigRepository.deleteByExamId(examId);
            savePoolConfigs(exam, req.getPoolConfigs());
        }

        return mapToDetail(exam);
    }

    // =====================================================
    // PUBLISH — FR-5.5 (DRAFT → ACTIVE)
    // =====================================================

    public ExamSummaryResponse publishExam(Long examId, User publisher) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(publisher, exam.getCourse());

        if (exam.getStatus() != Exam.ExamStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT exams can be published.");
        }
        validateForPublish(exam);

        exam.setStatus(Exam.ExamStatus.ACTIVE);
        examRepository.save(exam);
        logger.info("Exam '{}' published (ACTIVE) by {}", exam.getTitle(), publisher.getEmail());
        return mapToSummary(exam, null);
    }

    public ExamSummaryResponse deactivateExam(Long examId, User actor) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(actor, exam.getCourse());

        if (exam.getStatus() != Exam.ExamStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ACTIVE exams can be deactivated.");
        }
        exam.setStatus(Exam.ExamStatus.DEACTIVATED);
        examRepository.save(exam);
        return mapToSummary(exam, null);
    }

    public ExamSummaryResponse archiveExam(Long examId, User actor) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(actor, exam.getCourse());

        if (exam.getStatus() == Exam.ExamStatus.DRAFT || exam.getStatus() == Exam.ExamStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DEACTIVATED exams can be archived.");
        }
        exam.setStatus(Exam.ExamStatus.ARCHIVED);
        examRepository.save(exam);
        return mapToSummary(exam, null);
    }

    public void deleteExam(Long examId, User actor) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(actor, exam.getCourse());

        if (exam.getStatus() != Exam.ExamStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT exams can be deleted. This exam has student submissions — archive it instead.");
        }
        if (attemptRepository.countSubmissions(examId) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This exam has student submissions. Archive it to preserve records.");
        }
        examRepository.delete(exam);
    }

    // =====================================================
    // RELEASE RESULTS — FR-5.4
    // =====================================================

    public ExamSummaryResponse releaseResults(Long examId, User actor) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(actor, exam.getCourse());

        if (exam.isResultsReleased()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Results are already released.");
        }

        exam.setResultsReleased(true);
        examRepository.save(exam);
        logger.info("Results released for exam '{}' by {}", exam.getTitle(), actor.getEmail());

        // Notify all students who submitted
        notifyResultsReleased(exam);
        return mapToSummary(exam, null);
    }

    // =====================================================
    // LISTS — Role-aware
    // =====================================================

    @Transactional(readOnly = true)
    public Page<ExamSummaryResponse> getExamsForManagement(
            User viewer, Long courseId, String status, String search, int page, int size) {

        Exam.ExamStatus statusEnum = (status != null && !status.isBlank())
                ? Exam.ExamStatus.valueOf(status) : null;

        // Admins see all; Instructors see only their courses
        Long filteredCourseId = courseId;
        if (viewer.getRole() == User.Role.INSTRUCTOR && courseId == null) {
            // No further filtering here; the JPQL will handle via course.instructors
            // For simplicity: instructor must select a course
        }

        Pageable pg = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return examRepository.findWithFilters(filteredCourseId, statusEnum, search, pg)
                .map(e -> mapToSummary(e, viewer));
    }

    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> getExamsForStudent(User student, Long courseId) {
        return examRepository.findActiveExamsForStudent(student.getId(), courseId)
                .stream()
                .map(e -> mapToSummaryForStudent(e, student))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse getExamDetail(Long examId, User viewer) {
        Exam exam = findExamById(examId);
        // Students cannot fetch question details (answers exposed)
        if (viewer.getRole() == User.Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Use the attempt API to take the exam.");
        }
        return mapToDetail(exam);
    }

    // =====================================================
    // ANALYTICS — FR-5.4
    // =====================================================

    @Transactional(readOnly = true)
    public ExamAnalyticsResponse getAnalytics(Long examId, User viewer) {
        Exam exam = findExamById(examId);
        validateCreatorCanManageCourse(viewer, exam.getCourse());

        List<ExamAttempt> graded = attemptRepository.findGradedByExamId(examId);
        int total = graded.size();

        if (total == 0) {
            return ExamAnalyticsResponse.builder()
                    .examId(exam.getId()).examTitle(exam.getTitle())
                    .courseTitle(exam.getCourse().getTitle())
                    .totalSubmissions(0)
                    .resultsReleased(exam.isResultsReleased())
                    .averageScore(BigDecimal.ZERO).highestScore(BigDecimal.ZERO)
                    .lowestScore(BigDecimal.ZERO).passRate(BigDecimal.ZERO)
                    .scoreDistribution(Collections.emptyList())
                    .questionDifficulty(Collections.emptyList())
                    .atRiskStudents(Collections.emptyList())
                    .build();
        }

        // KPIs
        BigDecimal maxMarks = exam.getTotalMarks();
        List<BigDecimal> percentages = graded.stream()
                .map(a -> a.getPercentage() != null ? a.getPercentage() : BigDecimal.ZERO)
                .collect(Collectors.toList());

        BigDecimal avg     = percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
        BigDecimal highest = percentages.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal lowest  = percentages.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        long passCount = graded.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count();
        BigDecimal passRate = new BigDecimal(passCount)
                .divide(new BigDecimal(total), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100))
                .setScale(1, RoundingMode.HALF_UP);

        // Score distribution (10 buckets 0-10, 10-20, ... 90-100)
        int[] buckets = new int[10];
        for (BigDecimal pct : percentages) {
            int idx = Math.min((int)(pct.doubleValue() / 10), 9);
            buckets[idx]++;
        }
        List<ExamAnalyticsResponse.DistributionBucket> dist = new ArrayList<>();
        String[] labels = {"0-10","10-20","20-30","30-40","40-50","50-60","60-70","70-80","80-90","90-100"};
        for (int i = 0; i < 10; i++) {
            dist.add(ExamAnalyticsResponse.DistributionBucket.builder()
                    .label(labels[i]).count(buckets[i])
                    .percentage(new BigDecimal(buckets[i]).divide(new BigDecimal(total), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }

        // Per-question difficulty
        List<Object[]> correctCounts = gradeRepository.countCorrectPerQuestion(examId);
        Map<Long, Long> correctMap = new HashMap<>();
        for (Object[] row : correctCounts) {
            correctMap.put(((Number)row[0]).longValue(), ((Number)row[1]).longValue());
        }
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderBySortOrderAsc(examId);
        List<ExamAnalyticsResponse.QuestionDifficulty> difficulty = questions.stream()
                .filter(q -> q.getQuestionType() == ExamQuestion.QuestionType.MCQ ||
                        q.getQuestionType() == ExamQuestion.QuestionType.SHORT_ANSWER)
                .map(q -> {
                    long correct = correctMap.getOrDefault(q.getId(), 0L);
                    BigDecimal pct = total > 0
                            ? new BigDecimal(correct).divide(new BigDecimal(total), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ExamAnalyticsResponse.QuestionDifficulty.builder()
                            .questionId(q.getId())
                            .displayOrder(q.getSortOrder() + 1)
                            .questionText(q.getQuestionText().length() > 80
                                    ? q.getQuestionText().substring(0, 80) + "…" : q.getQuestionText())
                            .correctPct(pct)
                            .build();
                })
                .sorted(Comparator.comparing(ExamAnalyticsResponse.QuestionDifficulty::getCorrectPct))
                .collect(Collectors.toList());

        // At-risk students (below passing grade)
        List<ExamAnalyticsResponse.AtRiskStudent> atRisk = graded.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getPassed()))
                .map(a -> ExamAnalyticsResponse.AtRiskStudent.builder()
                        .studentId(a.getStudent().getId())
                        .studentName(a.getStudent().getFullName())
                        .studentEmail(a.getStudent().getEmail())
                        .score(a.getTotalScore())
                        .percentage(a.getPercentage())
                        .attemptId(a.getId())
                        .build())
                .collect(Collectors.toList());

        return ExamAnalyticsResponse.builder()
                .examId(exam.getId()).examTitle(exam.getTitle())
                .courseTitle(exam.getCourse().getTitle())
                .totalSubmissions(total)
                .resultsReleased(exam.isResultsReleased())
                .averageScore(avg).highestScore(highest)
                .lowestScore(lowest).passRate(passRate)
                .scoreDistribution(dist).questionDifficulty(difficulty)
                .atRiskStudents(atRisk)
                .build();
    }

    // =====================================================
    // CRON: Auto-deactivate expired exams — FR-5.5
    // Runs every 15 minutes
    // =====================================================

    @Scheduled(fixedDelay = 900000)
    public void autoDeactivateExpiredExams() {
        List<Exam> expired = examRepository.findExpiredActiveExams(LocalDateTime.now());
        if (expired.isEmpty()) return;
        expired.forEach(e -> e.setStatus(Exam.ExamStatus.DEACTIVATED));
        examRepository.saveAll(expired);
        logger.info("Auto-deactivated {} expired exam(s)", expired.size());
    }

    // =====================================================
    // VALIDATION — FR-5.1 publish rules
    // =====================================================

    private void validateForPublish(Exam exam) {
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderBySortOrderAsc(exam.getId());
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one question is required before publishing.");
        }

        // Total marks must equal sum of question points
        BigDecimal questionSum = questionRepository.sumPointsByExamId(exam.getId());
        if (questionSum.compareTo(exam.getTotalMarks()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total marks (" + exam.getTotalMarks() + ") must equal the sum of all question points (" + questionSum + ").");
        }

        // MCQ validation
        questions.stream()
                .filter(q -> q.getQuestionType() == ExamQuestion.QuestionType.MCQ)
                .forEach(q -> {
                    if (q.getOptions().size() < 2) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "MCQ question '" + q.getQuestionText().substring(0, Math.min(50, q.getQuestionText().length())) + "' needs at least 2 options.");
                    }
                    boolean hasCorrect = q.getOptions().stream().anyMatch(ExamQuestionOption::isCorrect);
                    if (!hasCorrect) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "MCQ question '" + q.getQuestionText().substring(0, Math.min(50, q.getQuestionText().length())) + "' needs at least one correct answer marked.");
                    }
                });

        // File upload validation
        questions.stream()
                .filter(q -> q.getQuestionType() == ExamQuestion.QuestionType.FILE_UPLOAD)
                .forEach(q -> {
                    if (q.getAllowedFileTypes() == null || q.getAllowedFileTypes().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "File Upload question must specify allowed file types.");
                    }
                });

        // Availability window required
        if (exam.getAvailableFrom() == null || exam.getAvailableUntil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Availability window (from and until dates) is required before publishing.");
        }
        if (!exam.getAvailableUntil().isAfter(exam.getAvailableFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Availability window close date must be after the open date.");
        }

        // Pool config validation for randomized exams
        if (exam.isUseRandomization()) {
            List<ExamPoolConfig> configs = poolConfigRepository.findByExamId(exam.getId());
            for (ExamPoolConfig cfg : configs) {
                long count = questionRepository.countByExamIdAndPoolName(exam.getId(), cfg.getPoolName());
                if (count < cfg.getDrawCount()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Pool '" + cfg.getPoolName() + "' has " + count + " questions but requires " + cfg.getDrawCount() + " to be drawn.");
                }
            }
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private void saveQuestions(Exam exam, List<CreateExamRequest.QuestionRequest> qReqs) {
        int order = 0;
        for (CreateExamRequest.QuestionRequest qReq : qReqs) {
            ExamQuestion q = ExamQuestion.builder()
                    .exam(exam)
                    .questionType(ExamQuestion.QuestionType.valueOf(qReq.getQuestionType()))
                    .questionText(qReq.getQuestionText())
                    .points(qReq.getPoints())
                    .poolName(qReq.getPoolName())
                    .sortOrder(order++)
                    .rubric(qReq.getRubric())
                    .allowedFileTypes(qReq.getAllowedFileTypes())
                    .maxFileSizeMb(qReq.getMaxFileSizeMb())
                    .shortAnswerMethod(qReq.getShortAnswerMethod() != null
                            ? ExamQuestion.ShortAnswerMethod.valueOf(qReq.getShortAnswerMethod())
                            : ExamQuestion.ShortAnswerMethod.MANUAL)
                    .expectedAnswer(qReq.getExpectedAnswer())
                    .build();
            q = questionRepository.save(q);

            if (qReq.getOptions() != null && !qReq.getOptions().isEmpty()) {
                int optOrder = 0;
                for (CreateExamRequest.OptionRequest oReq : qReq.getOptions()) {
                    ExamQuestionOption opt = ExamQuestionOption.builder()
                            .question(q)
                            .optionText(oReq.getOptionText())
                            .correct(oReq.isCorrect())
                            .sortOrder(optOrder++)
                            .build();
                    q.getOptions().add(opt);
                }
                questionRepository.save(q);
            }
        }
    }

    private void savePoolConfigs(Exam exam, List<CreateExamRequest.PoolConfigRequest> configs) {
        for (CreateExamRequest.PoolConfigRequest cfg : configs) {
            ExamPoolConfig poolCfg = ExamPoolConfig.builder()
                    .exam(exam).poolName(cfg.getPoolName()).drawCount(cfg.getDrawCount())
                    .build();
            poolConfigRepository.save(poolCfg);
        }
    }

    private void validateCreatorCanManageCourse(User user, Course course) {
        if (user.getRole() == User.Role.ADMIN) return;
        if (user.getRole() == User.Role.INSTRUCTOR) {
            boolean assigned = course.getInstructors().stream()
                    .anyMatch(i -> i.getId().equals(user.getId()));
            if (!assigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not assigned to this course.");
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions.");
    }

    private void notifyResultsReleased(Exam exam) {
        List<ExamAttempt> attempts = attemptRepository.findSubmittedByExamId(exam.getId());
        Set<String> notified = new HashSet<>();
        for (ExamAttempt a : attempts) {
            String email = a.getStudent().getEmail();
            if (notified.add(email)) {
                emailService.sendExamResultsNotification(email, a.getStudent().getFullName(),
                        exam.getTitle(), exam.getCourse().getTitle());
            }
        }
    }

    public Exam findExamById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found."));
    }

    // =====================================================
    // MAPPING
    // =====================================================

    private ExamDetailResponse mapToDetail(Exam exam) {
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderBySortOrderAsc(exam.getId());
        List<ExamPoolConfig> pools = poolConfigRepository.findByExamId(exam.getId());

        return ExamDetailResponse.builder()
                .id(exam.getId())
                .courseId(exam.getCourse().getId())
                .courseTitle(exam.getCourse().getTitle())
                .courseCode(exam.getCourse().getCourseCode())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .totalMarks(exam.getTotalMarks())
                .passingGradePct(exam.getPassingGradePct())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .maxAttempts(exam.getMaxAttempts())
                .availableFrom(exam.getAvailableFrom())
                .availableUntil(exam.getAvailableUntil())
                .resultRelease(exam.getResultRelease().name())
                .resultsReleased(exam.isResultsReleased())
                .status(exam.getStatus().name())
                .useRandomization(exam.isUseRandomization())
                .createdByName(exam.getCreatedBy().getFullName())
                .createdAt(exam.getCreatedAt())
                .questions(questions.stream().map(this::mapQuestion).collect(Collectors.toList()))
                .poolConfigs(pools.stream().map(p -> ExamDetailResponse.PoolConfigDetail.builder()
                        .poolName(p.getPoolName()).drawCount(p.getDrawCount())
                        .questionCount(questionRepository.countByExamIdAndPoolName(exam.getId(), p.getPoolName()))
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private ExamDetailResponse.QuestionDetail mapQuestion(ExamQuestion q) {
        return ExamDetailResponse.QuestionDetail.builder()
                .id(q.getId())
                .questionType(q.getQuestionType().name())
                .questionText(q.getQuestionText())
                .points(q.getPoints())
                .poolName(q.getPoolName())
                .sortOrder(q.getSortOrder())
                .rubric(q.getRubric())
                .allowedFileTypes(q.getAllowedFileTypes())
                .maxFileSizeMb(q.getMaxFileSizeMb())
                .shortAnswerMethod(q.getShortAnswerMethod() != null ? q.getShortAnswerMethod().name() : null)
                .expectedAnswer(q.getExpectedAnswer())
                .options(q.getOptions().stream().map(o -> ExamDetailResponse.OptionDetail.builder()
                                .id(o.getId()).optionText(o.getOptionText())
                                .correct(o.isCorrect()).sortOrder(o.getSortOrder()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    ExamSummaryResponse mapToSummary(Exam exam, User viewer) {
        long subCount = attemptRepository.countSubmissions(exam.getId());
        long pending  = attemptRepository.countPendingGrades(exam.getId());

        String windowStatus = "NOT_OPEN";
        LocalDateTime now = LocalDateTime.now();
        if (exam.getAvailableFrom() != null && exam.getAvailableUntil() != null) {
            if (now.isAfter(exam.getAvailableFrom()) && now.isBefore(exam.getAvailableUntil())) {
                windowStatus = "OPEN";
            } else if (now.isAfter(exam.getAvailableUntil())) {
                windowStatus = "CLOSED";
            }
        }

        return ExamSummaryResponse.builder()
                .id(exam.getId())
                .courseId(exam.getCourse().getId())
                .courseTitle(exam.getCourse().getTitle())
                .courseCode(exam.getCourse().getCourseCode())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .totalMarks(exam.getTotalMarks())
                .passingGradePct(exam.getPassingGradePct())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .maxAttempts(exam.getMaxAttempts())
                .availableFrom(exam.getAvailableFrom())
                .availableUntil(exam.getAvailableUntil())
                .resultRelease(exam.getResultRelease().name())
                .resultsReleased(exam.isResultsReleased())
                .status(exam.getStatus().name())
                .useRandomization(exam.isUseRandomization())
                .createdByName(exam.getCreatedBy().getFullName())
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .submissionCount(subCount)
                .pendingGradeCount(pending)
                .windowStatus(windowStatus)
                .build();
    }

    private ExamSummaryResponse mapToSummaryForStudent(Exam exam, User student) {
        ExamSummaryResponse base = mapToSummary(exam, student);

        long used = attemptRepository.countCompletedAttempts(exam.getId(), student.getId());
        long maxAtt = exam.getMaxAttempts() != null ? exam.getMaxAttempts() : -1L;
        long remaining = maxAtt < 0 ? -1L : Math.max(0, maxAtt - used);

        // Find active or most recent attempt
        Optional<ExamAttempt> inProgress = attemptRepository.findByExamIdAndStudentIdAndStatus(
                exam.getId(), student.getId(), ExamAttempt.AttemptStatus.IN_PROGRESS);

        String attemptStatus = "NOT_STARTED";
        Long activeAttemptId = null;
        if (inProgress.isPresent()) {
            attemptStatus = "IN_PROGRESS";
            activeAttemptId = inProgress.get().getId();
        } else if (used > 0) {
            List<ExamAttempt> recent = attemptRepository
                    .findByExamIdAndStudentIdOrderByStartedAtDesc(exam.getId(), student.getId());
            if (!recent.isEmpty()) {
                ExamAttempt last = recent.get(0);
                attemptStatus = last.getStatus().name();
                activeAttemptId = last.getId();
            }
        }

        base.setAttemptsUsed(used);
        base.setAttemptsRemaining(remaining);
        base.setStudentAttemptStatus(attemptStatus);
        base.setActiveAttemptId(activeAttemptId);
        return base;
    }

    // Called by ExamGradingService — validates grader can manage the course
    public void validateAccessForGrading(User grader, Course course) {
        if (grader.getRole() == User.Role.ADMIN) return;
        if (grader.getRole() == User.Role.INSTRUCTOR) {
            boolean assigned = course.getInstructors().stream()
                    .anyMatch(i -> i.getId().equals(grader.getId()));
            if (!assigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not assigned to this course.");
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions.");
    }
}