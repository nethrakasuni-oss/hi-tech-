package com.hitech.lms.course.service;
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

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AttendanceService — Business logic for course attendance management.
 *
 * Handles:
 * - Importing attendance from CSV content (upsert: updates existing records on re-upload)
 * - Retrieving attendance records by course and date
 * - Listing recorded attendance dates for a course
 *
 * Only instructors assigned to the course can import attendance.
 */
@Service
@Transactional
public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);

    @Autowired private AttendanceRepository  attendanceRepository;
    @Autowired private CourseService         courseService;
    @Autowired private EnrollmentRepository  enrollmentRepository;
    @Autowired private UserRepository        userRepository;

    // =====================================================
    // IMPORT ATTENDANCE FROM CSV
    // Upsert: updates existing records for the same date
    // =====================================================

    public AttendanceImportResult importAttendance(Long courseId, String csvContent, String dateStr, User instructor) {
        Course course = courseService.findCourseById(courseId);

        // Verify instructor is assigned to this course
        boolean isAssigned = course.getInstructors().stream()
                .anyMatch(i -> i.getId().equals(instructor.getId()));
        if (instructor.getRole() != User.Role.ADMIN && !isAssigned) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You can only manage attendance for courses assigned to you.");
        }

        // Parse the date
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD.");
        }

        // Don't allow future dates
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot record attendance for a future date.");
        }

        // Parse CSV lines
        List<String[]> rows = parseCsv(csvContent);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2; // +2 because we skipped the header and arrays are 0-indexed

            if (row.length < 2) {
                errors.add("Row " + rowNum + ": Invalid format — expected StudentEmail,Status");
                skipped++;
                continue;
            }

            String email = row[0].trim().toLowerCase();
            String statusStr = row[1].trim().toUpperCase();

            // Validate status
            Attendance.AttendanceStatus status;
            try {
                status = Attendance.AttendanceStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                errors.add("Row " + rowNum + " (" + email + "): Invalid status '" + statusStr
                        + "'. Must be PRESENT, ABSENT, LATE, or EXCUSED.");
                skipped++;
                continue;
            }

            // Look up student by email
            Optional<User> studentOpt = userRepository.findByEmail(email);
            if (studentOpt.isEmpty()) {
                errors.add("Row " + rowNum + ": Student '" + email + "' not found in the system.");
                skipped++;
                continue;
            }

            User student = studentOpt.get();

            // Verify student is enrolled in this course
            boolean isEnrolled = enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                    student.getId(), courseId, Enrollment.EnrollmentStatus.ACTIVE);
            if (!isEnrolled) {
                errors.add("Row " + rowNum + ": Student '" + email + "' is not actively enrolled in this course.");
                skipped++;
                continue;
            }

            // Upsert: find existing or create new
            Optional<Attendance> existingOpt = attendanceRepository
                    .findByCourseIdAndStudentIdAndDate(courseId, student.getId(), date);

            if (existingOpt.isPresent()) {
                // Update existing record
                Attendance existing = existingOpt.get();
                existing.setStatus(status);
                existing.setRecordedBy(instructor);
                attendanceRepository.save(existing);
                updated++;
            } else {
                // Create new record
                Attendance attendance = Attendance.builder()
                        .course(course)
                        .student(student)
                        .date(date)
                        .status(status)
                        .recordedBy(instructor)
                        .build();
                attendanceRepository.save(attendance);
                created++;
            }
        }

        logger.info("Attendance imported for course {} on {}: {} created, {} updated, {} skipped by {}",
                course.getCourseCode(), date, created, updated, skipped, instructor.getEmail());

        return AttendanceImportResult.builder()
                .date(date)
                .totalRows(rows.size())
                .created(created)
                .updated(updated)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    // =====================================================
    // GET ATTENDANCE FOR A SPECIFIC DATE
    // =====================================================

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByDate(Long courseId, String dateStr, User viewer) {
        Course course = courseService.findCourseById(courseId);

        // Only admin or assigned instructor can view attendance
        boolean isAssigned = course.getInstructors().stream()
                .anyMatch(i -> i.getId().equals(viewer.getId()));
        if (viewer.getRole() != User.Role.ADMIN && !isAssigned) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You do not have access to attendance for this course.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD.");
        }

        return attendanceRepository.findByCourseIdAndDate(courseId, date)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // =====================================================
    // GET DISTINCT ATTENDANCE DATES FOR A COURSE
    // =====================================================

    @Transactional(readOnly = true)
    public List<LocalDate> getAttendanceDates(Long courseId, User viewer) {
        Course course = courseService.findCourseById(courseId);

        // Only admin or assigned instructor can view attendance dates
        boolean isAssigned = course.getInstructors().stream()
                .anyMatch(i -> i.getId().equals(viewer.getId()));
        if (viewer.getRole() != User.Role.ADMIN && !isAssigned) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You do not have access to attendance for this course.");
        }

        return attendanceRepository.findDistinctDatesByCourseId(courseId);
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    /**
     * Parse CSV content into rows (skipping the header row).
     * Supports both \r\n and \n line endings.
     */
    private List<String[]> parseCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "CSV content is empty.");
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                // Skip empty lines
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",", -1);
                rows.add(parts);
            }
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Failed to parse CSV content: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "CSV file contains no data rows (only header or empty).");
        }

        return rows;
    }

    private AttendanceResponse mapToResponse(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId())
                .courseId(a.getCourse().getId())
                .studentId(a.getStudent().getId())
                .studentName(a.getStudent().getFullName())
                .studentEmail(a.getStudent().getEmail())
                .date(a.getDate())
                .status(a.getStatus().name())
                .recordedByName(a.getRecordedBy().getFullName())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
