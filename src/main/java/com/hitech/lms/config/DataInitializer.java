package com.hitech.lms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitech.lms.announcement.model.Announcement;
import com.hitech.lms.announcement.repository.AnnouncementRepository;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.auth.repository.UserRepository;
import com.hitech.lms.course.model.Attendance;
import com.hitech.lms.course.model.ContentNode;
import com.hitech.lms.course.model.Course;
import com.hitech.lms.course.model.CourseMaterial;
import com.hitech.lms.course.model.Enrollment;
import com.hitech.lms.course.model.Program;
import com.hitech.lms.course.repository.AttendanceRepository;
import com.hitech.lms.course.repository.ContentNodeRepository;
import com.hitech.lms.course.repository.CourseMaterialRepository;
import com.hitech.lms.course.repository.CourseRepository;
import com.hitech.lms.course.repository.EnrollmentRepository;
import com.hitech.lms.course.repository.ProgramRepository;
import com.hitech.lms.exam.model.AttemptAnswer;
import com.hitech.lms.exam.model.AttemptQuestion;
import com.hitech.lms.exam.model.AttemptQuestionGrade;
import com.hitech.lms.exam.model.Exam;
import com.hitech.lms.exam.model.ExamAttempt;
import com.hitech.lms.exam.model.ExamPoolConfig;
import com.hitech.lms.exam.model.ExamQuestion;
import com.hitech.lms.exam.model.ExamQuestionOption;
import com.hitech.lms.exam.repository.AttemptAnswerRepository;
import com.hitech.lms.exam.repository.AttemptQuestionGradeRepository;
import com.hitech.lms.exam.repository.ExamAttemptRepository;
import com.hitech.lms.exam.repository.ExamPoolConfigRepository;
import com.hitech.lms.exam.repository.ExamQuestionRepository;
import com.hitech.lms.exam.repository.ExamRepository;
import com.hitech.lms.finance.model.Invoice;
import com.hitech.lms.finance.model.Payment;
import com.hitech.lms.finance.model.PaymentIntent;
import com.hitech.lms.finance.repository.InvoiceRepository;
import com.hitech.lms.finance.repository.PaymentIntentRepository;
import com.hitech.lms.finance.repository.PaymentRepository;
import com.hitech.lms.schedule.model.ClassSession;
import com.hitech.lms.schedule.model.RecurrenceGroup;
import com.hitech.lms.schedule.repository.ClassSessionRepository;
import com.hitech.lms.schedule.repository.RecurrenceGroupRepository;
import com.hitech.lms.support.model.Appointment;
import com.hitech.lms.support.model.InstructorAvailability;
import com.hitech.lms.support.model.KnowledgeBaseArticle;
import com.hitech.lms.support.model.SupportTicket;
import com.hitech.lms.support.model.TicketMessage;
import com.hitech.lms.support.repository.AppointmentRepository;
import com.hitech.lms.support.repository.InstructorAvailabilityRepository;
import com.hitech.lms.support.repository.KnowledgeBaseArticleRepository;
import com.hitech.lms.support.repository.SupportTicketRepository;
import com.hitech.lms.support.repository.TicketMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "Password2000.";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private ContentNodeRepository contentNodeRepository;
    @Autowired private CourseMaterialRepository courseMaterialRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private ExamQuestionRepository examQuestionRepository;
    @Autowired private ExamPoolConfigRepository examPoolConfigRepository;
    @Autowired private ExamAttemptRepository examAttemptRepository;
    @Autowired private AttemptAnswerRepository attemptAnswerRepository;
    @Autowired private AttemptQuestionGradeRepository attemptQuestionGradeRepository;
    @Autowired private RecurrenceGroupRepository recurrenceGroupRepository;
    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private PaymentIntentRepository paymentIntentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private SupportTicketRepository supportTicketRepository;
    @Autowired private TicketMessageRepository ticketMessageRepository;
    @Autowired private KnowledgeBaseArticleRepository articleRepository;
    @Autowired private InstructorAvailabilityRepository availabilityRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AnnouncementRepository announcementRepository;

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Program> programs = new HashMap<>();
    private final Map<String, Course> courses = new HashMap<>();
    private final Map<String, ContentNode> nodes = new HashMap<>();
    private final Map<String, Exam> exams = new HashMap<>();
    private final Map<String, ExamQuestion> questions = new HashMap<>();
    private final Map<String, ExamAttempt> attempts = new HashMap<>();
    private final Map<String, PaymentIntent> intents = new HashMap<>();
    private final Map<String, Payment> payments = new HashMap<>();
    private final Map<String, SupportTicket> tickets = new HashMap<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        seedUsers();
        seedCourses();
        seedContent();
        seedExams();
        seedSchedule();
        seedFinance();
        seedSupport();
        seedAnnouncements();
        seedPredictionDemoData();
    }

    private void seedUsers() throws IOException {
        JsonNode root = readSeed("seed/01-users.json");
        for (JsonNode item : root.get("users")) {
            String email = text(item, "email").toLowerCase();
            User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(textOr(item, "password", DEFAULT_PASSWORD)))
                    .build());
            user.setFullName(text(item, "fullName"));
            user.setPhoneNumber(textOrNull(item, "phoneNumber"));
            user.setBio(textOrNull(item, "bio"));
            user.setLanguagePreference(textOr(item, "languagePreference", "en"));
            user.setProfilePhotoUrl(textOrNull(item, "profilePhotoUrl"));
            user.setRole(User.Role.valueOf(text(item, "role")));
            user.setAccountStatus(User.AccountStatus.valueOf(textOr(item, "accountStatus", "ACTIVE")));
            users.put(email, userRepository.save(user));
        }
    }

    private void seedCourses() throws IOException {
        JsonNode root = readSeed("seed/02-programs-courses.json");
        User admin = user("admin@hitech.com");
        for (JsonNode item : root.get("programs")) {
            String name = text(item, "name");
            Program program = programRepository.findAll().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseGet(Program::new);
            program.setName(name);
            program.setDescription(textOrNull(item, "description"));
            program.setIsActive(boolOr(item, "isActive", true));
            programs.put(name, programRepository.save(program));
        }
        for (JsonNode item : root.get("courses")) {
            String code = text(item, "courseCode");
            Course course = courseRepository.findAll().stream()
                    .filter(c -> c.getCourseCode().equalsIgnoreCase(code))
                    .findFirst()
                    .orElseGet(Course::new);
            course.setTitle(text(item, "title"));
            course.setCourseCode(code);
            course.setDescription(textOrNull(item, "description"));
            course.setProgram(program(text(item, "program")));
            course.setDurationValue(intOrNull(item, "durationValue"));
            course.setDurationUnit(textOrNull(item, "durationUnit"));
            course.setCourseFee(money(item, "courseFee"));
            course.setEnrollmentType(Course.EnrollmentType.valueOf(textOr(item, "enrollmentType", "OPEN")));
            course.setMaxStudents(intOrNull(item, "maxStudents"));
            course.setThumbnailUrl(textOrNull(item, "thumbnailUrl"));
            course.setCurrentProgress(textOrNull(item, "currentProgress"));
            course.setStatus(Course.CourseStatus.valueOf(textOr(item, "status", "DRAFT")));
            course.setCreatedBy(admin);
            List<User> instructors = new ArrayList<>();
            item.withArray("instructors").forEach(email -> instructors.add(user(email.asText())));
            course.setInstructors(instructors);
            courses.put(code, courseRepository.save(course));
        }
        for (JsonNode item : root.get("enrollments")) {
            User student = user(text(item, "student"));
            Course course = course(text(item, "courseCode"));
            Enrollment enrollment = enrollmentRepository.findAll().stream()
                    .filter(e -> e.getStudent().getId().equals(student.getId()) && e.getCourse().getId().equals(course.getId()))
                    .findFirst()
                    .orElseGet(Enrollment::new);
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setEnrolledBy(hasText(item, "enrolledBy") ? user(text(item, "enrolledBy")) : null);
            enrollment.setPaymentReference(textOrNull(item, "paymentReference"));
            enrollment.setStatus(Enrollment.EnrollmentStatus.valueOf(textOr(item, "status", "ACTIVE")));
            enrollmentRepository.save(enrollment);
        }
    }

    private void seedContent() throws IOException {
        JsonNode root = readSeed("seed/03-content-attendance.json");
        for (JsonNode item : root.get("contentNodes")) {
            Course course = course(text(item, "courseCode"));
            String key = nodeKey(course.getCourseCode(), text(item, "title"));
            ContentNode node = contentNodeRepository.findAll().stream()
                    .filter(n -> n.getCourse().getId().equals(course.getId()) && n.getTitle().equalsIgnoreCase(text(item, "title")))
                    .findFirst()
                    .orElseGet(ContentNode::new);
            node.setCourse(course);
            node.setParent(hasText(item, "parentTitle") ? nodes.get(nodeKey(course.getCourseCode(), text(item, "parentTitle"))) : null);
            node.setNodeType(ContentNode.NodeType.valueOf(text(item, "nodeType")));
            node.setTitle(text(item, "title"));
            node.setSortOrder(intOr(item, "sortOrder", 0));
            nodes.put(key, contentNodeRepository.save(node));
        }
        for (JsonNode item : root.get("materials")) {
            Course course = course(text(item, "courseCode"));
            CourseMaterial material = courseMaterialRepository.findAll().stream()
                    .filter(m -> m.getCourse().getId().equals(course.getId()) && m.getTitle().equalsIgnoreCase(text(item, "title")))
                    .findFirst()
                    .orElseGet(CourseMaterial::new);
            material.setCourse(course);
            material.setNode(hasText(item, "nodeTitle") ? nodes.get(nodeKey(course.getCourseCode(), text(item, "nodeTitle"))) : null);
            material.setTitle(text(item, "title"));
            material.setDescription(textOrNull(item, "description"));
            material.setMaterialType(CourseMaterial.MaterialType.valueOf(text(item, "materialType")));
            material.setFileUrl(text(item, "fileUrl"));
            material.setFileSizeBytes(longOrNull(item, "fileSizeBytes"));
            material.setVersion(intOr(item, "version", 1));
            material.setSortOrder(intOr(item, "sortOrder", 0));
            material.setUploadedBy(user(text(item, "uploadedBy")));
            courseMaterialRepository.save(material);
        }
        for (JsonNode item : root.get("attendance")) {
            Course course = course(text(item, "courseCode"));
            User student = user(text(item, "student"));
            LocalDate date = LocalDate.parse(text(item, "date"));
            Attendance attendance = attendanceRepository.findAll().stream()
                    .filter(a -> a.getCourse().getId().equals(course.getId()) && a.getStudent().getId().equals(student.getId()) && a.getDate().equals(date))
                    .findFirst()
                    .orElseGet(Attendance::new);
            attendance.setCourse(course);
            attendance.setStudent(student);
            attendance.setDate(date);
            attendance.setStatus(Attendance.AttendanceStatus.valueOf(text(item, "status")));
            attendance.setRecordedBy(user(text(item, "recordedBy")));
            attendanceRepository.save(attendance);
        }
    }

    private void seedExams() throws IOException {
        JsonNode root = readSeed("seed/04-exams.json");
        for (JsonNode item : root.get("exams")) {
            Course course = course(text(item, "courseCode"));
            String title = text(item, "title");
            Exam exam = examRepository.findAll().stream()
                    .filter(e -> e.getCourse().getId().equals(course.getId()) && e.getTitle().equalsIgnoreCase(title))
                    .findFirst()
                    .orElseGet(Exam::new);
            exam.setCourse(course);
            exam.setCreatedBy(user(text(item, "createdBy")));
            exam.setTitle(title);
            exam.setDescription(textOrNull(item, "description"));
            exam.setTotalMarks(money(item, "totalMarks"));
            exam.setPassingGradePct(money(item, "passingGradePct"));
            exam.setTimeLimitMinutes(intOrNull(item, "timeLimitMinutes"));
            exam.setMaxAttempts(intOrNull(item, "maxAttempts"));
            exam.setAvailableFrom(dateTimeOrNull(item, "availableFrom"));
            exam.setAvailableUntil(dateTimeOrNull(item, "availableUntil"));
            exam.setResultRelease(Exam.ResultRelease.valueOf(textOr(item, "resultRelease", "MANUAL")));
            exam.setResultsReleased(boolOr(item, "resultsReleased", false));
            exam.setStatus(Exam.ExamStatus.valueOf(textOr(item, "status", "DRAFT")));
            exam.setUseRandomization(boolOr(item, "useRandomization", false));
            exams.put(examKey(course.getCourseCode(), title), examRepository.save(exam));
        }
        for (JsonNode item : root.get("questions")) {
            Exam exam = exam(text(item, "courseCode"), text(item, "examTitle"));
            String key = questionKey(exam.getCourse().getCourseCode(), exam.getTitle(), text(item, "questionText"));
            ExamQuestion question = examQuestionRepository.findAll().stream()
                    .filter(q -> q.getExam().getId().equals(exam.getId()) && q.getQuestionText().equalsIgnoreCase(text(item, "questionText")))
                    .findFirst()
                    .orElseGet(ExamQuestion::new);
            question.setExam(exam);
            question.setQuestionType(ExamQuestion.QuestionType.valueOf(text(item, "questionType")));
            question.setQuestionText(text(item, "questionText"));
            question.setPoints(money(item, "points"));
            question.setPoolName(textOrNull(item, "poolName"));
            question.setSortOrder(intOr(item, "sortOrder", 0));
            question.setRubric(textOrNull(item, "rubric"));
            question.setAllowedFileTypes(textOrNull(item, "allowedFileTypes"));
            question.setMaxFileSizeMb(intOrNull(item, "maxFileSizeMb"));
            question.setShortAnswerMethod(ExamQuestion.ShortAnswerMethod.valueOf(textOr(item, "shortAnswerMethod", "MANUAL")));
            question.setExpectedAnswer(textOrNull(item, "expectedAnswer"));
            if (question.getOptions() == null) {
                question.setOptions(new ArrayList<>());
            }
            question = examQuestionRepository.save(question);
            questions.put(key, question);
            if (item.has("options")) {
                for (JsonNode optionNode : item.get("options")) {
                    ExamQuestionOption option = question.getOptions().stream()
                            .filter(o -> o.getOptionText().equalsIgnoreCase(text(optionNode, "optionText")))
                            .findFirst()
                            .orElseGet(ExamQuestionOption::new);
                    option.setQuestion(question);
                    option.setOptionText(text(optionNode, "optionText"));
                    option.setCorrect(boolOr(optionNode, "correct", false));
                    option.setSortOrder(intOr(optionNode, "sortOrder", 0));
                    if (option.getId() == null) {
                        question.getOptions().add(option);
                    }
                }
                examQuestionRepository.save(question);
            }
        }
        for (JsonNode item : root.get("poolConfigs")) {
            Exam exam = exam(text(item, "courseCode"), text(item, "examTitle"));
            ExamPoolConfig pool = examPoolConfigRepository.findAll().stream()
                    .filter(p -> p.getExam().getId().equals(exam.getId()) && p.getPoolName().equalsIgnoreCase(text(item, "poolName")))
                    .findFirst()
                    .orElseGet(ExamPoolConfig::new);
            pool.setExam(exam);
            pool.setPoolName(text(item, "poolName"));
            pool.setDrawCount(intOr(item, "drawCount", 1));
            examPoolConfigRepository.save(pool);
        }
        seedExamAttempts(root);
    }

    private void seedExamAttempts(JsonNode root) {
        for (JsonNode item : root.get("attempts")) {
            Exam exam = exam(text(item, "courseCode"), text(item, "examTitle"));
            User student = user(text(item, "student"));
            String key = examKey(exam.getCourse().getCourseCode(), exam.getTitle()) + "|" + student.getEmail();
            ExamAttempt attempt = examAttemptRepository.findAll().stream()
                    .filter(a -> a.getExam().getId().equals(exam.getId()) && a.getStudent().getId().equals(student.getId()))
                    .findFirst()
                    .orElseGet(ExamAttempt::new);
            attempt.setExam(exam);
            attempt.setStudent(student);
            attempt.setStatus(ExamAttempt.AttemptStatus.valueOf(text(item, "status")));
            attempt.setSubmittedAt(dateTimeOrNull(item, "submittedAt"));
            attempt.setTotalScore(moneyOrNull(item, "totalScore"));
            attempt.setPercentage(moneyOrNull(item, "percentage"));
            attempt.setPassed(boolOrNull(item, "passed"));
            attempt.setExpiresAt(dateTimeOrNull(item, "expiresAt"));
            if (attempt.getAttemptQuestions() == null) {
                attempt.setAttemptQuestions(new ArrayList<>());
            }
            attempts.put(key, examAttemptRepository.save(attempt));
        }
        for (JsonNode item : root.get("attemptAnswers")) {
            ExamAttempt attempt = attempts.get(examKey(text(item, "courseCode"), text(item, "examTitle")) + "|" + text(item, "student"));
            ExamQuestion question = questions.get(questionKey(text(item, "courseCode"), text(item, "examTitle"), text(item, "questionText")));
            if (attempt == null || question == null) continue;
            AttemptQuestion attemptQuestion = attempt.getAttemptQuestions().stream()
                    .filter(aq -> aq.getQuestion().getId().equals(question.getId()))
                    .findFirst()
                    .orElseGet(AttemptQuestion::new);
            attemptQuestion.setAttempt(attempt);
            attemptQuestion.setQuestion(question);
            attemptQuestion.setDisplayOrder(intOr(item, "displayOrder", question.getSortOrder()));
            attemptQuestion.setShuffledOptions(textOrNull(item, "shuffledOptions"));
            if (attemptQuestion.getId() == null) {
                attempt.getAttemptQuestions().add(attemptQuestion);
            }

            AttemptAnswer answer = attemptAnswerRepository.findAll().stream()
                    .filter(a -> a.getAttempt().getId().equals(attempt.getId()) && a.getQuestion().getId().equals(question.getId()))
                    .findFirst()
                    .orElseGet(AttemptAnswer::new);
            answer.setAttempt(attempt);
            answer.setQuestion(question);
            answer.setAnswerText(textOrNull(item, "answerText"));
            answer.setFlagged(boolOr(item, "flagged", false));
            attemptAnswerRepository.save(answer);

            if (hasText(item, "gradeStatus")) {
                AttemptQuestionGrade grade = attemptQuestionGradeRepository.findAll().stream()
                        .filter(g -> g.getAttempt().getId().equals(attempt.getId()) && g.getQuestion().getId().equals(question.getId()))
                        .findFirst()
                        .orElseGet(AttemptQuestionGrade::new);
                grade.setAttempt(attempt);
                grade.setQuestion(question);
                grade.setScoreAwarded(moneyOr(item, "scoreAwarded", BigDecimal.ZERO));
                grade.setMaxScore(question.getPoints());
                grade.setGradeStatus(AttemptQuestionGrade.GradeStatus.valueOf(text(item, "gradeStatus")));
                grade.setFeedback(textOrNull(item, "feedback"));
                grade.setGradedBy(hasText(item, "gradedBy") ? user(text(item, "gradedBy")) : null);
                grade.setGradedAt(dateTimeOrNull(item, "gradedAt"));
                attemptQuestionGradeRepository.save(grade);
            }
            examAttemptRepository.save(attempt);
        }
    }

    private void seedSchedule() throws IOException {
        JsonNode root = readSeed("seed/05-schedule.json");
        List<RecurrenceGroup> groups = new ArrayList<>();
        for (JsonNode item : root.get("recurrenceGroups")) {
            RecurrenceGroup group = recurrenceGroupRepository.findAll().stream()
                    .filter(g -> g.getFrequency().name().equals(text(item, "frequency")) && g.getRepeatUntil().equals(LocalDate.parse(text(item, "repeatUntil"))))
                    .findFirst()
                    .orElseGet(RecurrenceGroup::new);
            group.setFrequency(RecurrenceGroup.Frequency.valueOf(text(item, "frequency")));
            group.setDaysOfWeek(textOrNull(item, "daysOfWeek"));
            group.setRepeatUntil(LocalDate.parse(text(item, "repeatUntil")));
            groups.add(recurrenceGroupRepository.save(group));
        }
        for (JsonNode item : root.get("sessions")) {
            Course course = course(text(item, "courseCode"));
            LocalDate sessionDate = LocalDate.parse(text(item, "sessionDate"));
            LocalTime startTime = LocalTime.parse(text(item, "startTime"));
            ClassSession session = classSessionRepository.findAll().stream()
                    .filter(s -> s.getCourse().getId().equals(course.getId()) && s.getSessionDate().equals(sessionDate) && s.getStartTime().equals(startTime))
                    .findFirst()
                    .orElseGet(ClassSession::new);
            session.setCourse(course);
            session.setInstructor(user(text(item, "instructor")));
            session.setRecurrenceGroup(intOr(item, "recurrenceGroupIndex", 0) > 0 ? groups.get(intOr(item, "recurrenceGroupIndex", 1) - 1) : null);
            session.setTitle(text(item, "title"));
            session.setSessionDate(sessionDate);
            session.setStartTime(startTime);
            session.setEndTime(LocalTime.parse(text(item, "endTime")));
            session.setClassType(ClassSession.ClassType.valueOf(text(item, "classType")));
            session.setMeetingPlatform(hasText(item, "meetingPlatform") ? ClassSession.MeetingPlatform.valueOf(text(item, "meetingPlatform")) : null);
            session.setMeetingLink(textOrNull(item, "meetingLink"));
            session.setClassLocation(textOrNull(item, "classLocation"));
            session.setNotes(textOrNull(item, "notes"));
            session.setStatus(ClassSession.SessionStatus.valueOf(textOr(item, "status", "SCHEDULED")));
            session.setCreatedBy(user(text(item, "createdBy")));
            classSessionRepository.save(session);
        }
    }

    private void seedFinance() throws IOException {
        JsonNode root = readSeed("seed/06-finance.json");
        for (JsonNode item : root.get("paymentIntents")) {
            String key = text(item, "idempotencyKey");
            PaymentIntent intent = paymentIntentRepository.findAll().stream()
                    .filter(i -> i.getIdempotencyKey().equals(key))
                    .findFirst()
                    .orElseGet(PaymentIntent::new);
            intent.setStudent(user(text(item, "student")));
            intent.setCourse(course(text(item, "courseCode")));
            intent.setAmount(money(item, "amount"));
            intent.setCurrency(textOr(item, "currency", "LKR"));
            intent.setStatus(PaymentIntent.IntentStatus.valueOf(text(item, "status")));
            intent.setIdempotencyKey(key);
            intent.setExpiresAt(LocalDateTime.parse(text(item, "expiresAt")));
            intents.put(key, paymentIntentRepository.save(intent));
        }
        for (JsonNode item : root.get("payments")) {
            String gateway = text(item, "gatewayReference");
            Payment payment = paymentRepository.findAll().stream()
                    .filter(p -> p.getGatewayReference().equals(gateway))
                    .findFirst()
                    .orElseGet(Payment::new);
            PaymentIntent intent = intents.get(text(item, "idempotencyKey"));
            payment.setPaymentIntent(intent);
            payment.setStudent(intent.getStudent());
            payment.setCourse(intent.getCourse());
            payment.setAmount(money(item, "amount"));
            payment.setCurrency(textOr(item, "currency", "LKR"));
            payment.setStatus(Payment.PaymentStatus.valueOf(text(item, "status")));
            payment.setPaymentMethod(textOr(item, "paymentMethod", "MOCK_CARD"));
            payment.setCardLastFour(textOrNull(item, "cardLastFour"));
            payment.setCardBrand(textOrNull(item, "cardBrand"));
            payment.setGatewayReference(gateway);
            payment.setFailureReason(textOrNull(item, "failureReason"));
            payments.put(gateway, paymentRepository.save(payment));
        }
        for (JsonNode item : root.get("invoices")) {
            String number = text(item, "invoiceNumber");
            Invoice invoice = invoiceRepository.findAll().stream()
                    .filter(i -> i.getInvoiceNumber().equals(number))
                    .findFirst()
                    .orElseGet(Invoice::new);
            Payment payment = payments.get(text(item, "gatewayReference"));
            invoice.setPayment(payment);
            invoice.setStudent(payment.getStudent());
            invoice.setInvoiceNumber(number);
            invoice.setSubtotal(money(item, "subtotal"));
            invoice.setTaxRate(moneyOr(item, "taxRate", BigDecimal.ZERO));
            invoice.setTaxAmount(moneyOr(item, "taxAmount", BigDecimal.ZERO));
            invoice.setTotalAmount(money(item, "totalAmount"));
            invoice.setCurrency(textOr(item, "currency", "LKR"));
            invoice.setInvoiceHtml(textOrNull(item, "invoiceHtml"));
            invoice.setStatus(Invoice.InvoiceStatus.valueOf(textOr(item, "status", "GENERATED")));
            invoiceRepository.save(invoice);
        }
    }

    private void seedSupport() throws IOException {
        JsonNode root = readSeed("seed/07-support.json");
        for (JsonNode item : root.get("articles")) {
            KnowledgeBaseArticle article = articleRepository.findAll().stream()
                    .filter(a -> a.getTitle().equalsIgnoreCase(text(item, "title")) && a.getCategory().name().equals(text(item, "category")))
                    .findFirst()
                    .orElseGet(KnowledgeBaseArticle::new);
            article.setTitle(text(item, "title"));
            article.setCategory(KnowledgeBaseArticle.ArticleCategory.valueOf(text(item, "category")));
            article.setContent(text(item, "content"));
            article.setTags(textOrNull(item, "tags"));
            article.setStatus(KnowledgeBaseArticle.ArticleStatus.valueOf(textOr(item, "status", "DRAFT")));
            article.setVersion(intOr(item, "version", 1));
            article.setViewCount(longOr(item, "viewCount", 0));
            article.setHelpfulYes(longOr(item, "helpfulYes", 0));
            article.setHelpfulNo(longOr(item, "helpfulNo", 0));
            article.setAuthor(user(text(item, "author")));
            articleRepository.save(article);
        }
        for (JsonNode item : root.get("availability")) {
            User instructor = user(text(item, "instructor"));
            InstructorAvailability availability = availabilityRepository.findAll().stream()
                    .filter(a -> a.getInstructor().getId().equals(instructor.getId())
                            && String.valueOf(a.getDayOfWeek()).equals(textOr(item, "dayOfWeek", "null"))
                            && String.valueOf(a.getBlockedDate()).equals(textOr(item, "blockedDate", "null")))
                    .findFirst()
                    .orElseGet(InstructorAvailability::new);
            availability.setInstructor(instructor);
            availability.setDayOfWeek(hasText(item, "dayOfWeek") ? DayOfWeek.valueOf(text(item, "dayOfWeek")) : null);
            availability.setStartTime(hasText(item, "startTime") ? LocalTime.parse(text(item, "startTime")) : null);
            availability.setEndTime(hasText(item, "endTime") ? LocalTime.parse(text(item, "endTime")) : null);
            availability.setBlockedDate(hasText(item, "blockedDate") ? LocalDate.parse(text(item, "blockedDate")) : null);
            availability.setBlocked(boolOr(item, "blocked", false));
            availability.setSessionDurationMinutes(intOr(item, "sessionDurationMinutes", 30));
            availability.setBufferMinutes(intOr(item, "bufferMinutes", 10));
            availabilityRepository.save(availability);
        }
        for (JsonNode item : root.get("tickets")) {
            SupportTicket ticket = supportTicketRepository.findAll().stream()
                    .filter(t -> t.getTicketNumber().equals(text(item, "ticketNumber")))
                    .findFirst()
                    .orElseGet(SupportTicket::new);
            ticket.setTicketNumber(text(item, "ticketNumber"));
            ticket.setStudent(user(text(item, "student")));
            ticket.setAssignedTo(hasText(item, "assignedTo") ? user(text(item, "assignedTo")) : null);
            ticket.setCategory(SupportTicket.TicketCategory.valueOf(text(item, "category")));
            ticket.setSubject(text(item, "subject"));
            ticket.setDescription(text(item, "description"));
            ticket.setPriority(SupportTicket.TicketPriority.valueOf(textOr(item, "priority", "MEDIUM")));
            ticket.setStatus(SupportTicket.TicketStatus.valueOf(textOr(item, "status", "OPEN")));
            ticket.setResolutionNote(textOrNull(item, "resolutionNote"));
            ticket.setResolvedAt(dateTimeOrNull(item, "resolvedAt"));
            ticket.setClosedAt(dateTimeOrNull(item, "closedAt"));
            ticket = supportTicketRepository.save(ticket);
            tickets.put(ticket.getTicketNumber(), ticket);
            for (JsonNode messageNode : item.withArray("messages")) {
                TicketMessage message = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                        .filter(m -> m.getBody().equals(text(messageNode, "body")))
                        .findFirst()
                        .orElseGet(TicketMessage::new);
                message.setTicket(ticket);
                message.setSender(user(text(messageNode, "sender")));
                message.setBody(text(messageNode, "body"));
                message.setInternal(boolOr(messageNode, "internal", false));
                ticketMessageRepository.save(message);
            }
        }
        for (JsonNode item : root.get("appointments")) {
            User student = user(text(item, "student"));
            User instructor = user(text(item, "instructor"));
            LocalDate date = LocalDate.parse(text(item, "appointmentDate"));
            LocalTime start = LocalTime.parse(text(item, "startTime"));
            Appointment appointment = appointmentRepository.findAll().stream()
                    .filter(a -> a.getStudent().getId().equals(student.getId()) && a.getInstructor().getId().equals(instructor.getId()) && a.getAppointmentDate().equals(date) && a.getStartTime().equals(start))
                    .findFirst()
                    .orElseGet(Appointment::new);
            appointment.setStudent(student);
            appointment.setInstructor(instructor);
            appointment.setAppointmentDate(date);
            appointment.setStartTime(start);
            appointment.setEndTime(LocalTime.parse(text(item, "endTime")));
            appointment.setReason(Appointment.AppointmentReason.valueOf(text(item, "reason")));
            appointment.setNotes(textOrNull(item, "notes"));
            appointment.setCancellationReason(textOrNull(item, "cancellationReason"));
            appointment.setStatus(Appointment.AppointmentStatus.valueOf(textOr(item, "status", "PENDING")));
            appointmentRepository.save(appointment);
        }
    }

    private void seedAnnouncements() throws IOException {
        JsonNode root = readSeed("seed/08-announcements.json");
        for (JsonNode item : root.get("announcements")) {
            Announcement announcement = announcementRepository.findAll().stream()
                    .filter(a -> a.getTitle().equalsIgnoreCase(text(item, "title")))
                    .findFirst()
                    .orElseGet(Announcement::new);
            announcement.setTitle(text(item, "title"));
            announcement.setMessage(text(item, "message"));
            announcement.setType(Announcement.AnnouncementType.valueOf(text(item, "type")));
            announcement.setCourse(hasText(item, "courseCode") ? course(text(item, "courseCode")) : null);
            announcement.setAuthor(user(text(item, "author")));
            announcement.setActive(boolOr(item, "isActive", true));
            announcementRepository.save(announcement);
        }
    }

    private void seedPredictionDemoData() {
        LocalDate startDate = LocalDate.now().minusDays(18);
        User admin = userRepository.findByEmail("admin@hitech.com").orElseThrow();

        for (Enrollment enrollment : enrollmentRepository.findAll()) {
            Course course = enrollment.getCourse();
            User student = enrollment.getStudent();
            User instructor = course.getInstructors().isEmpty() ? admin : course.getInstructors().get(0);

            int band = performanceBand(student, course, enrollment);
            seedMissingAttendance(course, student, instructor, startDate, band);
            seedMissingExamAttempt(course, student, instructor, band);
        }
    }

    private void seedMissingAttendance(Course course, User student, User instructor, LocalDate startDate, int band) {
        Attendance.AttendanceStatus[][] profiles = {
                {
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT
                },
                {
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.LATE,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT
                },
                {
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.LATE,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT
                },
                {
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.LATE,
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT
                },
                {
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.PRESENT,
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.ABSENT,
                        Attendance.AttendanceStatus.LATE,
                        Attendance.AttendanceStatus.ABSENT
                }
        };

        Attendance.AttendanceStatus[] statuses = profiles[band];
        for (int i = 0; i < statuses.length; i++) {
            LocalDate date = startDate.plusDays(i * 2L);
            if (attendanceRepository.findByCourseIdAndStudentIdAndDate(course.getId(), student.getId(), date).isPresent()) {
                continue;
            }
            attendanceRepository.save(Attendance.builder()
                    .course(course)
                    .student(student)
                    .date(date)
                    .status(statuses[i])
                    .recordedBy(instructor)
                    .build());
        }
    }

    private void seedMissingExamAttempt(Course course, User student, User instructor, int band) {
        Exam exam = findOrCreatePredictionExam(course, instructor);
        ExamQuestion question = findOrCreatePredictionQuestion(exam);
        if (!examAttemptRepository.findByExamIdAndStudentIdOrderByStartedAtDesc(exam.getId(), student.getId()).isEmpty()) {
            return;
        }

        BigDecimal[] scores = {
                new BigDecimal("91.00"),
                new BigDecimal("76.00"),
                new BigDecimal("63.00"),
                new BigDecimal("48.00"),
                new BigDecimal("32.00")
        };
        BigDecimal score = scores[band];

        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .exam(exam)
                .student(student)
                .status(ExamAttempt.AttemptStatus.GRADED)
                .submittedAt(LocalDateTime.now().minusDays(Math.max(1, band + 1)))
                .totalScore(score)
                .percentage(score)
                .passed(score.compareTo(new BigDecimal("50.00")) >= 0)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build());

        attemptAnswerRepository.save(AttemptAnswer.builder()
                .attempt(attempt)
                .question(question)
                .answerText("Seeded checkpoint answer for prediction demo data.")
                .flagged(band >= 3)
                .build());

        AttemptQuestionGrade.GradeStatus gradeStatus = score.compareTo(new BigDecimal("80.00")) >= 0
                ? AttemptQuestionGrade.GradeStatus.CORRECT
                : score.compareTo(new BigDecimal("50.00")) >= 0
                    ? AttemptQuestionGrade.GradeStatus.PARTIAL
                    : AttemptQuestionGrade.GradeStatus.INCORRECT;

        attemptQuestionGradeRepository.save(AttemptQuestionGrade.builder()
                .attempt(attempt)
                .question(question)
                .scoreAwarded(score)
                .maxScore(new BigDecimal("100.00"))
                .gradeStatus(gradeStatus)
                .feedback("Seeded grade used for ML prediction demo coverage.")
                .gradedBy(instructor)
                .gradedAt(LocalDateTime.now().minusDays(Math.max(1, band)))
                .build());
    }

    private Exam findOrCreatePredictionExam(Course course, User instructor) {
        return examRepository.findAll().stream()
                .filter(e -> e.getCourse().getId().equals(course.getId())
                        && e.getTitle().equalsIgnoreCase("Performance Prediction Checkpoint"))
                .findFirst()
                .orElseGet(() -> examRepository.save(Exam.builder()
                        .course(course)
                        .createdBy(instructor)
                        .title("Performance Prediction Checkpoint")
                        .description("Seeded checkpoint assessment used to provide demo data for ML predictions.")
                        .totalMarks(new BigDecimal("100.00"))
                        .passingGradePct(new BigDecimal("50.00"))
                        .timeLimitMinutes(45)
                        .maxAttempts(1)
                        .availableFrom(LocalDateTime.now().minusDays(30))
                        .availableUntil(LocalDateTime.now().plusDays(30))
                        .resultRelease(Exam.ResultRelease.IMMEDIATE)
                        .resultsReleased(true)
                        .status(Exam.ExamStatus.ACTIVE)
                        .useRandomization(false)
                        .build()));
    }

    private ExamQuestion findOrCreatePredictionQuestion(Exam exam) {
        return examQuestionRepository.findByExamIdOrderBySortOrderAsc(exam.getId()).stream()
                .filter(q -> q.getQuestionText().equalsIgnoreCase("Checkpoint performance task"))
                .findFirst()
                .orElseGet(() -> examQuestionRepository.save(ExamQuestion.builder()
                        .exam(exam)
                        .questionType(ExamQuestion.QuestionType.SHORT_ANSWER)
                        .questionText("Checkpoint performance task")
                        .points(new BigDecimal("100.00"))
                        .poolName("Prediction")
                        .sortOrder(1)
                        .shortAnswerMethod(ExamQuestion.ShortAnswerMethod.MANUAL)
                        .expectedAnswer("Demonstrates course understanding.")
                        .build()));
    }

    private int performanceBand(User student, Course course, Enrollment enrollment) {
        if (student.getAccountStatus() == User.AccountStatus.INACTIVE || enrollment.getStatus() == Enrollment.EnrollmentStatus.REMOVED) {
            return 4;
        }
        int band = Math.floorMod((student.getEmail() + course.getCourseCode()).hashCode(), 5);
        if (enrollment.getStatus() == Enrollment.EnrollmentStatus.COMPLETED) {
            return Math.min(band, 1);
        }
        return band;
    }

    private JsonNode readSeed(String path) throws IOException {
        return objectMapper.readTree(new ClassPathResource(path).getInputStream());
    }

    private User user(String email) {
        String key = email.toLowerCase();
        return Optional.ofNullable(users.get(key)).orElseGet(() -> userRepository.findByEmail(key).orElseThrow());
    }

    private Program program(String name) {
        return Optional.ofNullable(programs.get(name)).orElseGet(() -> programRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElseThrow());
    }

    private Course course(String code) {
        return Optional.ofNullable(courses.get(code)).orElseGet(() -> courseRepository.findAll().stream()
                .filter(c -> c.getCourseCode().equalsIgnoreCase(code)).findFirst().orElseThrow());
    }

    private Exam exam(String code, String title) {
        return Optional.ofNullable(exams.get(examKey(code, title))).orElseGet(() -> examRepository.findAll().stream()
                .filter(e -> e.getCourse().getCourseCode().equalsIgnoreCase(code) && e.getTitle().equalsIgnoreCase(title))
                .findFirst().orElseThrow());
    }

    private String examKey(String code, String title) {
        return code + "|" + title.toLowerCase();
    }

    private String questionKey(String code, String examTitle, String questionText) {
        return examKey(code, examTitle) + "|" + questionText.toLowerCase();
    }

    private String nodeKey(String code, String title) {
        return code + "|" + title.toLowerCase();
    }

    private String text(JsonNode node, String field) {
        return node.get(field).asText();
    }

    private String textOr(JsonNode node, String field, String fallback) {
        return hasText(node, field) ? node.get(field).asText() : fallback;
    }

    private String textOrNull(JsonNode node, String field) {
        return hasText(node, field) ? node.get(field).asText() : null;
    }

    private boolean hasText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() && !node.get(field).asText().isBlank();
    }

    private int intOr(JsonNode node, String field, int fallback) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : fallback;
    }

    private Integer intOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private long longOr(JsonNode node, String field, long fallback) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : fallback;
    }

    private Long longOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private boolean boolOr(JsonNode node, String field, boolean fallback) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : fallback;
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : null;
    }

    private BigDecimal money(JsonNode node, String field) {
        return moneyOr(node, field, BigDecimal.ZERO);
    }

    private BigDecimal moneyOr(JsonNode node, String field, BigDecimal fallback) {
        return node.has(field) && !node.get(field).isNull() ? new BigDecimal(node.get(field).asText()) : fallback;
    }

    private BigDecimal moneyOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? new BigDecimal(node.get(field).asText()) : null;
    }

    private LocalDateTime dateTimeOrNull(JsonNode node, String field) {
        return hasText(node, field) ? LocalDateTime.parse(text(node, field)) : null;
    }
}
