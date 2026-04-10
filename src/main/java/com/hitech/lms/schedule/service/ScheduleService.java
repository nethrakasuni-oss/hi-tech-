package com.hitech.lms.schedule.service;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ScheduleService — Business logic for FR-4.1 (Class Scheduling).
 *
 * Responsibilities:
 * 1. Create single and recurring sessions (recurrence engine)
 * 2. Update sessions (scope: this only / this and future / all)
 * 3. Cancel / delete sessions
 * 4. Calendar queries (role-aware)
 * 5. Upcoming sessions panel
 * 6. Instructor conflict detection
 * 7. Email notifications to enrolled students + instructor
 */
@Service
@Transactional
public class ScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleService.class);

    @Autowired private ClassSessionRepository    sessionRepository;
    @Autowired private RecurrenceGroupRepository recurrenceGroupRepository;
    @Autowired private CourseService             courseService;
    @Autowired private UserRepository            userRepository;
    @Autowired private EnrollmentRepository      enrollmentRepository;
    @Autowired private EmailService              emailService;

    // =====================================================
    // CREATE SESSION(S) — FR-4.1 Steps 89-96
    // =====================================================

    public List<SessionResponse> createSession(CreateSessionRequest req, User admin) {
        Course course     = courseService.findCourseById(req.getCourseId());
        User   instructor = findInstructor(req.getInstructorId());

        validateSessionTimes(req.getStartTime(), req.getEndTime());
        validateSessionMode(req.getClassType(), req.getMeetingPlatform(),
                req.getMeetingLink(), req.getClassLocation());

        // Conflict check (FR-4.1: warning, but Admin can override)
        if (!req.isOverrideConflict()) {
            ConflictCheckResponse conflict = checkConflict(
                    req.getInstructorId(), req.getSessionDate(),
                    req.getStartTime(), req.getEndTime(), null);
            if (conflict.isHasConflict()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, conflict.getConflictMessage());
            }
        }

        List<ClassSession> sessions = new ArrayList<>();

        if (!req.isRecurring()) {
            // Single session
            sessions.add(buildSession(req, course, instructor, admin, null));
        } else {
            // Recurring sessions — generate dates
            validateRecurrenceRequest(req);
            RecurrenceGroup group = recurrenceGroupRepository.save(
                    RecurrenceGroup.builder()
                            .frequency(RecurrenceGroup.Frequency.valueOf(req.getRecurrenceFrequency()))
                            .daysOfWeek(req.getDaysOfWeek())
                            .repeatUntil(req.getRepeatUntil())
                            .build());

            List<LocalDate> dates = generateRecurrenceDates(
                    req.getSessionDate(), req.getRecurrenceFrequency(),
                    req.getDaysOfWeek(), req.getRepeatUntil());

            for (LocalDate date : dates) {
                CreateSessionRequest copy = copyRequest(req, date);
                sessions.add(buildSession(copy, course, instructor, admin, group));
            }
        }

        List<ClassSession> saved = sessionRepository.saveAll(sessions);
        logger.info("Created {} session(s) for course {} by admin {}",
                saved.size(), course.getCourseCode(), admin.getEmail());

        // FR-4.3: Send notifications to enrolled students + instructor
        // Use @Async in EmailService so this doesn't block the response
        notifySessionCreated(saved, course, instructor);

        return saved.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // =====================================================
    // UPDATE SESSION — FR-4.1
    // editScope: THIS_ONLY / THIS_AND_FUTURE / ALL
    // =====================================================

    public List<SessionResponse> updateSession(Long sessionId, UpdateSessionRequest req, User admin) {
        ClassSession session = findSessionById(sessionId);
        User instructor = findInstructor(req.getInstructorId());

        validateSessionTimes(req.getStartTime(), req.getEndTime());
        validateSessionMode(req.getClassType(), req.getMeetingPlatform(),
                req.getMeetingLink(), req.getClassLocation());

        if (!req.isOverrideConflict()) {
            ConflictCheckResponse conflict = checkConflict(
                    req.getInstructorId(), req.getSessionDate(),
                    req.getStartTime(), req.getEndTime(), sessionId);
            if (conflict.isHasConflict()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, conflict.getConflictMessage());
            }
        }

        List<ClassSession> toUpdate = new ArrayList<>();

        if (session.getRecurrenceGroup() == null || "THIS_ONLY".equals(req.getEditScope())) {
            toUpdate.add(session);
        } else if ("THIS_AND_FUTURE".equals(req.getEditScope())) {
            toUpdate = sessionRepository.findFutureInGroup(
                    session.getRecurrenceGroup().getId(), session.getSessionDate());
        } else { // ALL
            toUpdate = sessionRepository.findByRecurrenceGroupIdOrderBySessionDateAsc(
                    session.getRecurrenceGroup().getId());
        }

        for (ClassSession s : toUpdate) {
            s.setTitle(req.getTitle());
            s.setInstructor(instructor);
            s.setStartTime(req.getStartTime());
            s.setEndTime(req.getEndTime());
            s.setClassType(req.getClassType());
            s.setMeetingPlatform(req.getMeetingPlatform());   // null for PHYSICAL
            s.setMeetingLink(req.getMeetingLink());            // null for PHYSICAL
            s.setClassLocation(req.getClassLocation());        // null for ONLINE
            s.setNotes(req.getNotes());
            // For THIS_ONLY, also update the date
            if ("THIS_ONLY".equals(req.getEditScope())) {
                s.setSessionDate(req.getSessionDate());
            }
        }

        List<ClassSession> saved = sessionRepository.saveAll(toUpdate);
        logger.info("Updated {} session(s) [scope={}] by admin {}",
                saved.size(), req.getEditScope(), admin.getEmail());

        // Notify: send update emails
        notifySessionUpdated(saved, session.getCourse(), instructor, req.getEditScope());

        return saved.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // =====================================================
    // CANCEL SESSION — FR-4.1
    // =====================================================

    public SessionResponse cancelSession(Long sessionId, String editScope, User currentUser) {
        ClassSession session = findSessionById(sessionId);

        // Instructors can only cancel their own sessions
        if (currentUser.getRole() == User.Role.INSTRUCTOR && !session.getInstructor().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own sessions.");
        }

        if (session.getStatus() == ClassSession.SessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is already cancelled.");
        }

        List<ClassSession> toCancel = getSessionsForScope(session, editScope);
        toCancel.forEach(s -> s.setStatus(ClassSession.SessionStatus.CANCELLED));
        sessionRepository.saveAll(toCancel);

        logger.info("Cancelled {} session(s) by {}", toCancel.size(), currentUser.getEmail());
        notifySessionCancelled(toCancel, session.getCourse(), session.getInstructor(), editScope);

        return mapToResponse(session);
    }

    // =====================================================
    // DELETE SESSION — FR-4.1
    // Only non-cancelled SCHEDULED sessions with no attendance records can be deleted.
    // (Attendance module is in scope of Module 5, so we allow delete freely here.)
    // =====================================================

    public void deleteSession(Long sessionId, String editScope, User admin) {
        ClassSession session = findSessionById(sessionId);
        List<ClassSession> toDelete = getSessionsForScope(session, editScope);
        sessionRepository.deleteAll(toDelete);
        logger.info("Deleted {} session(s) by admin {}", toDelete.size(), admin.getEmail());
    }

    // =====================================================
    // CALENDAR QUERIES — Role-aware date range fetch
    // =====================================================

    @Transactional(readOnly = true)
    public List<SessionResponse> getCalendarSessions(
            User viewer, LocalDate from, LocalDate to, Long courseId) {

        List<ClassSession> sessions;

        switch (viewer.getRole()) {
            case ADMIN:
                sessions = sessionRepository.findAllInRange(from, to, courseId);
                break;
            case INSTRUCTOR:
                sessions = sessionRepository.findByInstructorInRange(
                        viewer.getId(), from, to, courseId);
                break;
            case STUDENT:
                sessions = sessionRepository.findByStudentEnrollmentInRange(
                        viewer.getId(), from, to, courseId);
                break;
            default:
                sessions = List.of();
        }

        return sessions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // =====================================================
    // UPCOMING SESSIONS — Side panel (next 5)
    // =====================================================

    @Transactional(readOnly = true)
    public List<UpcomingSessionsResponse> getUpcomingSessions(User viewer, int limit) {
        LocalDate today = LocalDate.now();
        PageRequest pg  = PageRequest.of(0, limit);
        List<ClassSession> sessions;

        switch (viewer.getRole()) {
            case ADMIN:
                sessions = sessionRepository.findUpcomingAll(today, pg);
                break;
            case INSTRUCTOR:
                sessions = sessionRepository.findUpcomingForInstructor(today, viewer.getId(), pg);
                break;
            default: // STUDENT
                sessions = sessionRepository.findUpcomingForStudent(viewer.getId(), today, pg);
        }

        return sessions.stream().map(this::mapToUpcoming).collect(Collectors.toList());
    }

    // =====================================================
    // CONFLICT CHECK — FR-4.1: warning before save
    // =====================================================

    @Transactional(readOnly = true)
    public ConflictCheckResponse checkConflict(
            Long instructorId, LocalDate date,
            LocalTime startTime, LocalTime endTime, Long excludeSessionId) {

        Long exclude = excludeSessionId != null ? excludeSessionId : -1L;
        List<ClassSession> conflicts = sessionRepository.findConflicts(
                instructorId, date, startTime, endTime, exclude);

        if (conflicts.isEmpty()) {
            return ConflictCheckResponse.builder().hasConflict(false).build();
        }

        ClassSession c = conflicts.get(0);
        String msg = String.format(
                "Warning: %s already has a session '%s' for course '%s' at %s–%s on %s. You may still save.",
                c.getInstructor().getFullName(),
                c.getTitle(),
                c.getCourse().getTitle(),
                c.getStartTime(), c.getEndTime(), c.getSessionDate());

        return ConflictCheckResponse.builder()
                .hasConflict(true)
                .conflictMessage(msg)
                .conflictingSessionId(c.getId())
                .conflictingSessionTitle(c.getTitle())
                .conflictingCourseTitle(c.getCourse().getTitle())
                .conflictingDate(c.getSessionDate())
                .conflictingStartTime(c.getStartTime())
                .conflictingEndTime(c.getEndTime())
                .build();
    }

    // =====================================================
    // GET SINGLE SESSION
    // =====================================================

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId, User viewer) {
        ClassSession session = findSessionById(sessionId);
        // Students must be enrolled in the course to view session details
        if (viewer.getRole() == User.Role.STUDENT) {
            boolean enrolled = enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                    viewer.getId(), session.getCourse().getId(), Enrollment.EnrollmentStatus.ACTIVE);
            if (!enrolled) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not enrolled in this course.");
            }
        }
        return mapToResponse(session);
    }

    // =====================================================
    // DASHBOARD STATS
    // =====================================================

    @Transactional(readOnly = true)
    public long countUpcomingThisWeek(User viewer) {
        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.plusDays(6);
        return switch (viewer.getRole()) {
            case INSTRUCTOR -> sessionRepository.countUpcomingThisWeekForInstructor(
                    viewer.getId(), today, endOfWeek);
            case STUDENT -> sessionRepository.countUpcomingThisWeekForStudent(
                    viewer.getId(), today, endOfWeek);
            default -> 0L;
        };
    }

    // =====================================================
    // RECURRENCE ENGINE — FR-4.1
    // Generates all dates for a recurring session series.
    // =====================================================

    private List<LocalDate> generateRecurrenceDates(
            LocalDate startDate, String frequency,
            String daysOfWeek, LocalDate repeatUntil) {

        List<LocalDate> dates = new ArrayList<>();
        LocalDate maxDate = startDate.plusMonths(12); // FR-4.1: max 12 months ahead
        LocalDate until   = repeatUntil.isAfter(maxDate) ? maxDate : repeatUntil;

        switch (frequency.toUpperCase()) {
            case "DAILY" -> {
                LocalDate d = startDate;
                while (!d.isAfter(until)) { dates.add(d); d = d.plusDays(1); }
            }
            case "WEEKLY" -> {
                List<DayOfWeek> days = parseDaysOfWeek(daysOfWeek);
                LocalDate d = startDate;
                while (!d.isAfter(until)) {
                    if (days.contains(d.getDayOfWeek())) dates.add(d);
                    d = d.plusDays(1);
                }
            }
            case "BIWEEKLY" -> {
                // Start from the first day of the week containing startDate
                List<DayOfWeek> days = parseDaysOfWeek(daysOfWeek);
                LocalDate weekStart = startDate;
                while (!weekStart.isAfter(until)) {
                    for (int i = 0; i < 7; i++) {
                        LocalDate d = weekStart.plusDays(i);
                        if (!d.isBefore(startDate) && !d.isAfter(until)
                                && days.contains(d.getDayOfWeek())) {
                            dates.add(d);
                        }
                    }
                    weekStart = weekStart.plusWeeks(2);
                }
            }
            case "MONTHLY" -> {
                LocalDate d = startDate;
                while (!d.isAfter(until)) { dates.add(d); d = d.plusMonths(1); }
            }
        }
        return dates;
    }

    private List<DayOfWeek> parseDaysOfWeek(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(d -> switch (d.toUpperCase()) {
                    case "MON" -> DayOfWeek.MONDAY;
                    case "TUE" -> DayOfWeek.TUESDAY;
                    case "WED" -> DayOfWeek.WEDNESDAY;
                    case "THU" -> DayOfWeek.THURSDAY;
                    case "FRI" -> DayOfWeek.FRIDAY;
                    case "SAT" -> DayOfWeek.SATURDAY;
                    case "SUN" -> DayOfWeek.SUNDAY;
                    default    -> throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown day: " + d);
                })
                .collect(Collectors.toList());
    }

    // =====================================================
    // NOTIFICATIONS — FR-4.3 (simplified: email only, no WebSocket yet)
    // =====================================================

    private void notifySessionCreated(List<ClassSession> sessions, Course course, User instructor) {
        if (sessions.isEmpty()) return;
        ClassSession first = sessions.get(0);
        List<String> studentEmails = getEnrolledStudentEmails(course.getId());

        String subject = "New Class Session: " + first.getTitle() + " — " + course.getTitle();
        String sessionInfo = buildSessionSummaryText(sessions);

        // Notify students
        for (String email : studentEmails) {
            emailService.sendScheduleNotificationEmail(email, subject,
                    "A new class session has been scheduled for <strong>" + course.getTitle() + "</strong>.",
                    sessionInfo);
        }
        // Notify instructor
        emailService.sendScheduleNotificationEmail(instructor.getEmail(), subject,
                "You have been assigned to teach a new session for <strong>" + course.getTitle() + "</strong>.",
                sessionInfo);
    }

    private void notifySessionUpdated(List<ClassSession> sessions, Course course,
                                      User instructor, String scope) {
        if (sessions.isEmpty()) return;
        ClassSession first = sessions.get(0);
        List<String> studentEmails = getEnrolledStudentEmails(course.getId());

        String subject = "Class Session Updated: " + first.getTitle() + " — " + course.getTitle();
        String note = sessions.size() > 1 ? "Your recurring class schedule has been updated." :
                "A class session has been updated.";
        String sessionInfo = buildSessionSummaryText(sessions);

        for (String email : studentEmails) {
            emailService.sendScheduleNotificationEmail(email, subject, note, sessionInfo);
        }
        emailService.sendScheduleNotificationEmail(instructor.getEmail(), subject, note, sessionInfo);
    }

    private void notifySessionCancelled(List<ClassSession> sessions, Course course,
                                        User instructor, String scope) {
        if (sessions.isEmpty()) return;
        ClassSession first = sessions.get(0);
        List<String> studentEmails = getEnrolledStudentEmails(course.getId());

        String subject = "Session Cancelled: " + first.getTitle() + " — " + course.getTitle();
        String note = sessions.size() > 1
                ? "The following class sessions for <strong>" + course.getTitle() + "</strong> have been cancelled."
                : "A class session for <strong>" + course.getTitle() + "</strong> has been cancelled.";
        String sessionInfo = buildSessionSummaryText(sessions);

        for (String email : studentEmails) {
            emailService.sendScheduleNotificationEmail(email, subject, note, sessionInfo);
        }
        emailService.sendScheduleNotificationEmail(instructor.getEmail(), subject, note, sessionInfo);
    }

    private List<String> getEnrolledStudentEmails(Long courseId) {
        // Fetch all active enrollments for the course and collect student emails
        return enrollmentRepository
                .findByCourseIdWithFilters(courseId, null, Enrollment.EnrollmentStatus.ACTIVE,
                        org.springframework.data.domain.PageRequest.of(0, 1000))
                .stream()
                .map(e -> e.getStudent().getEmail())
                .collect(Collectors.toList());
    }

    private String buildSessionSummaryText(List<ClassSession> sessions) {
        if (sessions.size() == 1) {
            ClassSession s   = sessions.get(0);
            boolean isOnline = s.getClassType() == null
                    || s.getClassType() == ClassSession.ClassType.ONLINE;
            String locationPart = isOnline
                    ? String.format("🔗 <a href='%s'>Join Meeting</a>", s.getMeetingLink())
                    : String.format("📍 %s", s.getClassLocation() != null ? s.getClassLocation() : "See location");
            return String.format("<strong>%s</strong><br>📅 %s &nbsp; ⏰ %s – %s<br>%s",
                    s.getTitle(), s.getSessionDate(), s.getStartTime(), s.getEndTime(), locationPart);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("The following sessions are affected:<br><ul>");
        sessions.stream().limit(5).forEach(s ->
                sb.append(String.format("<li><strong>%s</strong> — %s %s–%s</li>",
                        s.getTitle(), s.getSessionDate(), s.getStartTime(), s.getEndTime())));
        if (sessions.size() > 5) sb.append("<li>…and ").append(sessions.size() - 5).append(" more</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private ClassSession buildSession(CreateSessionRequest req, Course course,
                                      User instructor, User admin, RecurrenceGroup group) {
        return ClassSession.builder()
                .course(course)
                .instructor(instructor)
                .recurrenceGroup(group)
                .title(req.getTitle().trim())
                .sessionDate(req.getSessionDate())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .classType(req.getClassType())
                // ONLINE fields — null when classType = PHYSICAL
                .meetingPlatform(req.getMeetingPlatform())
                .meetingLink(req.getMeetingLink())
                // PHYSICAL field — null when classType = ONLINE
                .classLocation(req.getClassLocation())
                .notes(req.getNotes())
                .status(ClassSession.SessionStatus.SCHEDULED)
                .createdBy(admin)
                .build();
    }

    private CreateSessionRequest copyRequest(CreateSessionRequest original, LocalDate newDate) {
        CreateSessionRequest copy = new CreateSessionRequest();
        copy.setCourseId(original.getCourseId());
        copy.setInstructorId(original.getInstructorId());
        copy.setTitle(original.getTitle());
        copy.setSessionDate(newDate);
        copy.setStartTime(original.getStartTime());
        copy.setEndTime(original.getEndTime());
        copy.setClassType(original.getClassType());
        copy.setMeetingPlatform(original.getMeetingPlatform());
        copy.setMeetingLink(original.getMeetingLink());
        copy.setClassLocation(original.getClassLocation());
        copy.setNotes(original.getNotes());
        return copy;
    }

    private List<ClassSession> getSessionsForScope(ClassSession session, String scope) {
        if (session.getRecurrenceGroup() == null || "THIS_ONLY".equals(scope)) {
            return List.of(session);
        } else if ("THIS_AND_FUTURE".equals(scope)) {
            return sessionRepository.findFutureInGroup(
                    session.getRecurrenceGroup().getId(), session.getSessionDate());
        } else { // ALL
            return sessionRepository.findByRecurrenceGroupIdOrderBySessionDateAsc(
                    session.getRecurrenceGroup().getId());
        }
    }

    private void validateSessionTimes(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "End time must be after start time.");
        }
    }

    private void validateSessionMode(ClassSession.ClassType classType,
                                     ClassSession.MeetingPlatform meetingPlatform,
                                     String meetingLink,
                                     String classLocation) {
        if (classType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Class type (ONLINE / PHYSICAL) is required.");
        }
        if (classType == ClassSession.ClassType.ONLINE) {
            if (meetingPlatform == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Meeting platform is required for online sessions.");
            }
            if (meetingLink == null || !meetingLink.startsWith("https://")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Meeting link must be a valid HTTPS URL for online sessions.");
            }
        } else { // PHYSICAL
            if (classLocation == null || classLocation.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Class location is required for physical sessions.");
            }
        }
    }

    private void validateRecurrenceRequest(CreateSessionRequest req) {
        if (req.getRepeatUntil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Repeat until date is required for recurring sessions.");
        }
        if (!req.getRepeatUntil().isAfter(req.getSessionDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Repeat until date must be after the session date.");
        }
        if (req.getSessionDate().plusMonths(12).isBefore(req.getRepeatUntil())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sessions cannot be scheduled more than 12 months in advance.");
        }
        String freq = req.getRecurrenceFrequency();
        if ("WEEKLY".equalsIgnoreCase(freq) || "BIWEEKLY".equalsIgnoreCase(freq)) {
            if (req.getDaysOfWeek() == null || req.getDaysOfWeek().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "At least one day of the week must be selected for " + freq + " sessions.");
            }
        }
    }

    private User findInstructor(Long instructorId) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Instructor not found."));
        if (instructor.getRole() != User.Role.INSTRUCTOR && instructor.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The selected user is not an Instructor.");
        }
        if (instructor.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The selected instructor account is not active.");
        }
        return instructor;
    }

    public ClassSession findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Session not found."));
    }

    // ---- Compute the "Live / Upcoming / Ended / Cancelled" label ----
    private String computeSessionState(ClassSession s) {
        if (s.getStatus() == ClassSession.SessionStatus.CANCELLED) return "Cancelled";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.of(s.getSessionDate(), s.getStartTime());
        LocalDateTime end   = LocalDateTime.of(s.getSessionDate(), s.getEndTime());
        if (now.isAfter(end))   return "Ended";
        if (now.isBefore(start)) return "Upcoming";
        return "Live";
    }

    // ---- Map ClassSession → SessionResponse ----
    private SessionResponse mapToResponse(ClassSession s) {
        return SessionResponse.builder()
                .id(s.getId())
                .courseId(s.getCourse().getId())
                .courseTitle(s.getCourse().getTitle())
                .courseCode(s.getCourse().getCourseCode())
                .programName(s.getCourse().getProgram() != null ? s.getCourse().getProgram().getName() : "")
                .instructorId(s.getInstructor().getId())
                .instructorName(s.getInstructor().getFullName())
                .instructorEmail(s.getInstructor().getEmail())
                .recurrenceGroupId(s.getRecurrenceGroup() != null ? s.getRecurrenceGroup().getId() : null)
                .recurrenceFrequency(s.getRecurrenceGroup() != null ?
                        s.getRecurrenceGroup().getFrequency().name() : null)
                .daysOfWeek(s.getRecurrenceGroup() != null ? s.getRecurrenceGroup().getDaysOfWeek() : null)
                .title(s.getTitle())
                .sessionDate(s.getSessionDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                // classType — null-safe fallback keeps legacy ONLINE rows working
                .classType(s.getClassType() != null ? s.getClassType().name() : ClassSession.ClassType.ONLINE.name())
                // meetingPlatform is null for PHYSICAL sessions — do NOT call .name() directly
                .meetingPlatform(s.getMeetingPlatform() != null ? s.getMeetingPlatform().name() : null)
                .meetingLink(s.getMeetingLink())
                .classLocation(s.getClassLocation())
                .notes(s.getNotes())
                .status(s.getStatus().name())
                .createdByName(s.getCreatedBy().getFullName())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .sessionState(computeSessionState(s))
                .build();
    }

    private UpcomingSessionsResponse mapToUpcoming(ClassSession s) {
        return UpcomingSessionsResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .courseTitle(s.getCourse().getTitle())
                .courseCode(s.getCourse().getCourseCode())
                .instructorName(s.getInstructor().getFullName())
                .sessionDate(s.getSessionDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                // classType — null-safe fallback keeps legacy ONLINE rows working
                .classType(s.getClassType() != null ? s.getClassType().name() : ClassSession.ClassType.ONLINE.name())
                // meetingPlatform is null for PHYSICAL sessions — do NOT call .name() directly
                .meetingPlatform(s.getMeetingPlatform() != null ? s.getMeetingPlatform().name() : null)
                .meetingLink(s.getMeetingLink())
                .classLocation(s.getClassLocation())
                .status(s.getStatus().name())
                .sessionState(computeSessionState(s))
                .build();
    }
}