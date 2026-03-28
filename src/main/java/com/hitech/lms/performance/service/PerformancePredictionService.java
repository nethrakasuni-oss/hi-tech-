package com.hitech.lms.performance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.course.model.Attendance;
import com.hitech.lms.course.model.Course;
import com.hitech.lms.course.model.CourseMaterial;
import com.hitech.lms.course.model.Enrollment;
import com.hitech.lms.course.repository.AttendanceRepository;
import com.hitech.lms.course.repository.CourseMaterialRepository;
import com.hitech.lms.course.repository.CourseRepository;
import com.hitech.lms.course.repository.EnrollmentRepository;
import com.hitech.lms.exam.model.AttemptAnswer;
import com.hitech.lms.exam.model.AttemptQuestionGrade;
import com.hitech.lms.exam.model.Exam;
import com.hitech.lms.exam.model.ExamAttempt;
import com.hitech.lms.exam.repository.AttemptAnswerRepository;
import com.hitech.lms.exam.repository.AttemptQuestionGradeRepository;
import com.hitech.lms.exam.repository.ExamAttemptRepository;
import com.hitech.lms.exam.repository.ExamRepository;
import com.hitech.lms.finance.model.Invoice;
import com.hitech.lms.finance.model.Payment;
import com.hitech.lms.finance.repository.InvoiceRepository;
import com.hitech.lms.finance.repository.PaymentRepository;
import com.hitech.lms.performance.dto.PerformancePredictionResponse;
import com.hitech.lms.schedule.model.ClassSession;
import com.hitech.lms.schedule.repository.ClassSessionRepository;
import com.hitech.lms.support.model.Appointment;
import com.hitech.lms.support.model.SupportTicket;
import com.hitech.lms.support.repository.AppointmentRepository;
import com.hitech.lms.support.repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PerformancePredictionService {

    @Value("${ml.python-command:python}")
    private String pythonCommand;

    @Value("${ml.predict-script:student_performance_predict.py}")
    private String predictScript;

    @Value("${ml.project-dir:.}")
    private String mlProjectDir;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private CourseMaterialRepository materialRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private ExamAttemptRepository attemptRepository;
    @Autowired private AttemptQuestionGradeRepository gradeRepository;
    @Autowired private AttemptAnswerRepository answerRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private SupportTicketRepository ticketRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public PerformancePredictionResponse predictCoursePerformance(Long courseId, User viewer) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));
        verifyCanPredict(course, viewer);

        List<Enrollment> enrollments = enrollmentRepository.findAll().stream()
                .filter(e -> e.getCourse().getId().equals(courseId))
                .sorted(Comparator.comparing(e -> e.getStudent().getFullName()))
                .toList();

        List<Map<String, Object>> featureRows = new ArrayList<>();
        List<StudentFeatureContext> contexts = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            StudentFeatureContext context = buildFeatureContext(course, enrollment);
            contexts.add(context);
            featureRows.add(context.features());
        }

        List<ModelPrediction> modelPredictions = callPythonModel(featureRows);
        List<PerformancePredictionResponse.StudentPrediction> predictions = new ArrayList<>();
        for (int i = 0; i < contexts.size(); i++) {
            StudentFeatureContext context = contexts.get(i);
            ModelPrediction prediction = i < modelPredictions.size()
                    ? modelPredictions.get(i)
                    : new ModelPrediction("Unavailable", null);

            predictions.add(PerformancePredictionResponse.StudentPrediction.builder()
                    .studentId(context.student().getId())
                    .studentName(context.student().getFullName())
                    .studentEmail(context.student().getEmail())
                    .enrollmentStatus(context.enrollment().getStatus().name())
                    .performanceCategory(prediction.performanceCategory())
                    .confidence(prediction.confidence())
                    .attendancePercentage(bigDecimal(context.features().get("attendance_percentage")))
                    .averageExamPercentage(bigDecimal(context.features().get("avg_exam_percentage")))
                    .supportTicketCount(number(context.features().get("support_ticket_count")))
                    .lastActivityDaysAgo(number(context.features().get("last_activity_days_ago")))
                    .features(context.features())
                    .build());
        }

        return PerformancePredictionResponse.builder()
                .courseId(course.getId())
                .courseCode(course.getCourseCode())
                .courseTitle(course.getTitle())
                .predictions(predictions)
                .build();
    }

    private void verifyCanPredict(Course course, User viewer) {
        if (viewer.getRole() == User.Role.ADMIN) {
            return;
        }
        boolean assigned = course.getInstructors().stream()
                .anyMatch(instructor -> instructor.getId().equals(viewer.getId()));
        if (viewer.getRole() != User.Role.INSTRUCTOR || !assigned) {
            throw new IllegalArgumentException("Only admins or assigned instructors can predict this course.");
        }
    }

    private StudentFeatureContext buildFeatureContext(Course course, Enrollment enrollment) {
        User student = enrollment.getStudent();
        Long courseId = course.getId();
        Long studentId = student.getId();
        LocalDate today = LocalDate.now();

        List<Attendance> attendance = attendanceRepository.findAll().stream()
                .filter(a -> a.getCourse().getId().equals(courseId) && a.getStudent().getId().equals(studentId))
                .toList();
        List<ClassSession> sessions = classSessionRepository.findAll().stream()
                .filter(s -> s.getCourse().getId().equals(courseId))
                .toList();
        List<CourseMaterial> materials = materialRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        List<Exam> exams = examRepository.findAll().stream()
                .filter(e -> e.getCourse().getId().equals(courseId))
                .toList();
        List<ExamAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .filter(a -> a.getExam().getCourse().getId().equals(courseId))
                .toList();
        List<Long> attemptIds = attempts.stream().map(ExamAttempt::getId).toList();
        List<AttemptQuestionGrade> grades = gradeRepository.findAll().stream()
                .filter(g -> attemptIds.contains(g.getAttempt().getId()))
                .toList();
        List<AttemptAnswer> answers = answerRepository.findAll().stream()
                .filter(a -> attemptIds.contains(a.getAttempt().getId()))
                .toList();
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getStudent().getId().equals(studentId) && p.getCourse().getId().equals(courseId))
                .toList();
        List<Long> paymentIds = payments.stream().map(Payment::getId).toList();
        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(i -> paymentIds.contains(i.getPayment().getId()))
                .toList();
        List<SupportTicket> tickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStudent().getId().equals(studentId))
                .toList();
        List<Appointment> appointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getStudent().getId().equals(studentId)
                        && course.getInstructors().stream().anyMatch(i -> i.getId().equals(a.getInstructor().getId())))
                .toList();

        long present = countAttendance(attendance, Attendance.AttendanceStatus.PRESENT);
        long late = countAttendance(attendance, Attendance.AttendanceStatus.LATE);
        long absent = countAttendance(attendance, Attendance.AttendanceStatus.ABSENT);
        long excused = countAttendance(attendance, Attendance.AttendanceStatus.EXCUSED);
        BigDecimal attendancePercentage = attendance.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((present + (late * 0.5) + excused) * 100.0 / attendance.size()).setScale(2, RoundingMode.HALF_UP);

        List<BigDecimal> percentages = attempts.stream()
                .map(ExamAttempt::getPercentage)
                .filter(p -> p != null)
                .toList();
        BigDecimal avgExam = average(percentages);
        BigDecimal bestExam = percentages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowestExam = percentages.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal scoreSum = BigDecimal.ZERO;
        BigDecimal maxScoreSum = BigDecimal.ZERO;
        for (AttemptQuestionGrade grade : grades) {
            if (grade.getScoreAwarded() != null) scoreSum = scoreSum.add(grade.getScoreAwarded());
            if (grade.getMaxScore() != null) maxScoreSum = maxScoreSum.add(grade.getMaxScore());
        }
        BigDecimal avgQuestionScore = maxScoreSum.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : scoreSum.multiply(BigDecimal.valueOf(100)).divide(maxScoreSum, 2, RoundingMode.HALF_UP);

        long successPayments = payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS).count();
        long failedPayments = payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count();
        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("student_account_status", student.getAccountStatus().name());
        features.put("student_role", student.getRole().name());
        features.put("course_status", course.getStatus().name());
        features.put("program_name", course.getProgram().getName());
        features.put("enrollment_status", enrollment.getStatus().name());
        features.put("enrollment_type", course.getEnrollmentType().name());
        features.put("course_fee", value(course.getCourseFee()));
        features.put("duration_value", value(course.getDurationValue()));
        features.put("duration_unit", safe(course.getDurationUnit()));
        features.put("instructor_count", course.getInstructors().size());
        features.put("days_since_enrollment", enrollment.getEnrolledAt() == null ? 0 : ChronoUnit.DAYS.between(enrollment.getEnrolledAt().toLocalDate(), today));
        features.put("attendance_total_sessions", attendance.size());
        features.put("attendance_present_count", present);
        features.put("attendance_late_count", late);
        features.put("attendance_absent_count", absent);
        features.put("attendance_excused_count", excused);
        features.put("attendance_percentage", attendancePercentage);
        features.put("completed_class_sessions", countSessions(sessions, ClassSession.SessionStatus.COMPLETED));
        features.put("scheduled_class_sessions", countSessions(sessions, ClassSession.SessionStatus.SCHEDULED));
        features.put("cancelled_class_sessions", countSessions(sessions, ClassSession.SessionStatus.CANCELLED));
        features.put("online_session_count", sessions.stream().filter(s -> s.getClassType() == ClassSession.ClassType.ONLINE).count());
        features.put("physical_session_count", sessions.stream().filter(s -> s.getClassType() == ClassSession.ClassType.PHYSICAL).count());
        features.put("materials_total", materials.size());
        features.put("pdf_material_count", countMaterials(materials, CourseMaterial.MaterialType.PDF));
        features.put("video_material_count", countMaterials(materials, CourseMaterial.MaterialType.VIDEO_LINK));
        features.put("external_url_material_count", countMaterials(materials, CourseMaterial.MaterialType.EXTERNAL_URL));
        features.put("exams_available", exams.size());
        features.put("exams_attempted", attempts.size());
        features.put("exams_submitted", attempts.stream().filter(a -> a.getStatus() != ExamAttempt.AttemptStatus.IN_PROGRESS).count());
        features.put("exams_graded", attempts.stream().filter(a -> a.getStatus() == ExamAttempt.AttemptStatus.GRADED).count());
        features.put("avg_exam_percentage", avgExam);
        features.put("best_exam_percentage", bestExam);
        features.put("lowest_exam_percentage", lowestExam);
        features.put("passed_exam_count", attempts.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count());
        features.put("failed_exam_count", attempts.stream().filter(a -> Boolean.FALSE.equals(a.getPassed())).count());
        features.put("manual_pending_grade_count", grades.stream().filter(g -> g.getGradeStatus() == AttemptQuestionGrade.GradeStatus.MANUAL_PENDING).count());
        features.put("flagged_answer_count", answers.stream().filter(AttemptAnswer::isFlagged).count());
        features.put("avg_question_score_percentage", avgQuestionScore);
        features.put("payment_status", paymentStatus(course, successPayments, failedPayments));
        features.put("successful_payment_count", successPayments);
        features.put("failed_payment_count", failedPayments);
        features.put("total_paid_amount", totalPaid);
        features.put("invoice_count", invoices.size());
        features.put("support_ticket_count", tickets.size());
        features.put("open_ticket_count", tickets.stream().filter(this::isOpenTicket).count());
        features.put("critical_ticket_count", tickets.stream().filter(t -> t.getPriority() == SupportTicket.TicketPriority.CRITICAL).count());
        features.put("academic_ticket_count", tickets.stream().filter(t -> t.getCategory() == SupportTicket.TicketCategory.ACADEMIC).count());
        features.put("technical_ticket_count", tickets.stream().filter(t -> t.getCategory() == SupportTicket.TicketCategory.TECHNICAL).count());
        features.put("finance_ticket_count", tickets.stream().filter(t -> t.getCategory() == SupportTicket.TicketCategory.FINANCE).count());
        features.put("appointment_count", appointments.size());
        features.put("completed_appointment_count", appointments.stream().filter(a -> a.getStatus() == Appointment.AppointmentStatus.COMPLETED).count());
        features.put("cancelled_appointment_count", appointments.stream().filter(a -> a.getStatus() == Appointment.AppointmentStatus.CANCELLED).count());
        features.put("last_activity_days_ago", lastActivityDaysAgo(today, attendance, attempts, tickets, appointments));

        return new StudentFeatureContext(student, enrollment, features);
    }

    private List<ModelPrediction> callPythonModel(List<Map<String, Object>> featureRows) {
        if (featureRows.isEmpty()) {
            return List.of();
        }
        try {
            File workingDirectory = new File(mlProjectDir).getAbsoluteFile();
            File scriptFile = new File(workingDirectory, predictScript);
            if (!scriptFile.exists()) {
                throw new IllegalStateException("Prediction script not found: " + scriptFile.getAbsolutePath());
            }

            Process process = new ProcessBuilder(pythonCommand, predictScript)
                    .directory(workingDirectory)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                objectMapper.writeValue(writer, Map.of("records", featureRows));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(error.isBlank() ? "Python predictor failed." : error);
            }
            JsonNode root = objectMapper.readTree(output);
            List<ModelPrediction> predictions = new ArrayList<>();
            for (JsonNode item : root.withArray("predictions")) {
                BigDecimal confidence = item.has("confidence")
                        ? BigDecimal.valueOf(item.get("confidence").asDouble()).setScale(2, RoundingMode.HALF_UP)
                        : null;
                predictions.add(new ModelPrediction(item.get("performanceCategory").asText(), confidence));
            }
            return predictions;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to run ML prediction: " + ex.getMessage(),
                    ex
            );
        }
    }

    private long countAttendance(List<Attendance> attendance, Attendance.AttendanceStatus status) {
        return attendance.stream().filter(a -> a.getStatus() == status).count();
    }

    private long countSessions(List<ClassSession> sessions, ClassSession.SessionStatus status) {
        return sessions.stream().filter(s -> s.getStatus() == status).count();
    }

    private long countMaterials(List<CourseMaterial> materials, CourseMaterial.MaterialType type) {
        return materials.stream().filter(m -> m.getMaterialType() == type).count();
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean isOpenTicket(SupportTicket ticket) {
        return ticket.getStatus() == SupportTicket.TicketStatus.OPEN
                || ticket.getStatus() == SupportTicket.TicketStatus.IN_PROGRESS
                || ticket.getStatus() == SupportTicket.TicketStatus.PENDING_STUDENT;
    }

    private String paymentStatus(Course course, long successPayments, long failedPayments) {
        if (course.getCourseFee() == null || course.getCourseFee().compareTo(BigDecimal.ZERO) == 0) {
            return "NOT_REQUIRED";
        }
        if (successPayments > 0) return "SUCCESS";
        if (failedPayments > 0) return "FAILED";
        return "PENDING";
    }

    private long lastActivityDaysAgo(
            LocalDate today,
            List<Attendance> attendance,
            List<ExamAttempt> attempts,
            List<SupportTicket> tickets,
            List<Appointment> appointments) {

        LocalDate latest = attendance.stream()
                .map(Attendance::getDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        latest = max(latest, attempts.stream()
                .map(a -> a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getStartedAt())
                .filter(d -> d != null)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null));
        latest = max(latest, tickets.stream()
                .map(SupportTicket::getCreatedAt)
                .filter(d -> d != null)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null));
        latest = max(latest, appointments.stream()
                .map(Appointment::getAppointmentDate)
                .max(LocalDate::compareTo)
                .orElse(null));
        return latest == null ? 999 : Math.max(0, ChronoUnit.DAYS.between(latest, today));
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private Object value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal bigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
    }

    private Integer number(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

    private record StudentFeatureContext(User student, Enrollment enrollment, Map<String, Object> features) {}
    private record ModelPrediction(String performanceCategory, BigDecimal confidence) {}
}
