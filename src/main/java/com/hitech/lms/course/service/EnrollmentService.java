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
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * EnrollmentService — Business logic for FR-2.3 (Enrollment Management & Access Control).
 *
 * Manages:
 * - Admin manually enrolling students
 * - Removing enrollments
 * - Access control check (is this student enrolled?)
 * - Paginated enrollment list for a course
 */
@Service
@Transactional
public class EnrollmentService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);

    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private CourseService        courseService;
    @Autowired private UserRepository       userRepository;

    // =====================================================
    // ADMIN: ENROLL A STUDENT IN A COURSE
    // FR-2.3 Step 64: Admin manually enrolls a student
    // =====================================================

    public EnrollmentResponse enrollStudent(Long courseId, EnrollStudentRequest request, User adminUser) {
        Course course = courseService.findCourseById(courseId);

        // Cannot enroll into an ARCHIVED course (FR-2.3)
        if (course.getStatus() == Course.CourseStatus.ARCHIVED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot enroll students into an archived course.");
        }

        User student = userRepository.findById(request.getStudentId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Student not found."));

        // Must be a STUDENT role
        if (student.getRole() != User.Role.STUDENT) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Only students can be enrolled in courses.");
        }

        // Duplicate check (FR-2.3: 409 Conflict)
        if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "This student is already enrolled in this course.");
        }

        // Capacity check (FR-2.3)
        if (course.getMaxStudents() != null) {
            long currentEnrollments = enrollmentRepository.countByCourseIdAndStatus(
                courseId, Enrollment.EnrollmentStatus.ACTIVE);
            if (currentEnrollments >= course.getMaxStudents()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This course is full. Maximum capacity (" + course.getMaxStudents() + ") reached.");
            }
        }

        Enrollment enrollment = Enrollment.builder()
            .student(student)
            .course(course)
            .enrolledBy(adminUser)
            .paymentReference(request.getPaymentReference())
            .status(Enrollment.EnrollmentStatus.ACTIVE)
            .build();

        enrollment = enrollmentRepository.save(enrollment);
        logger.info("Student {} enrolled in course {} by admin {}",
            student.getEmail(), course.getCourseCode(), adminUser.getEmail());

        return mapToResponse(enrollment);
    }

    // =====================================================
    // ADMIN: REMOVE ENROLLMENT
    // FR-2.3: Access revoked, data preserved
    // =====================================================

    public void removeEnrollment(Long courseId, Long enrollmentId, User adminUser) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Enrollment not found."));

        if (!enrollment.getCourse().getId().equals(courseId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Enrollment does not belong to this course.");
        }

        // Mark as REMOVED (data is preserved — never physically deleted)
        enrollment.setStatus(Enrollment.EnrollmentStatus.REMOVED);
        enrollmentRepository.save(enrollment);

        logger.info("Enrollment {} removed by admin {}", enrollmentId, adminUser.getEmail());
    }

    // =====================================================
    // ACCESS CONTROL CHECK
    // FR-2.3: Only enrolled students can access course content
    // =====================================================

    @Transactional(readOnly = true)
    public void verifyEnrollmentAccess(Long courseId, Long studentId) {
        boolean hasAccess = enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
            studentId, courseId, Enrollment.EnrollmentStatus.ACTIVE);

        if (!hasAccess) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not enrolled in this course. Please contact the administrator or enroll via the Finance portal."
            );
        }
    }

    // =====================================================
    // GET ENROLLMENTS FOR A COURSE (paginated, searchable)
    // FR-2.3: Admin Enrollment Management Screen
    // =====================================================

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> getEnrollmentsForCourse(
            Long courseId, String search, String status, int page, int size) {

        // Ensure course exists
        courseService.findCourseById(courseId);

        Enrollment.EnrollmentStatus statusEnum = (status != null && !status.isBlank())
            ? Enrollment.EnrollmentStatus.valueOf(status) : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt"));

        return enrollmentRepository.findByCourseIdWithFilters(courseId, search, statusEnum, pageable)
            .map(this::mapToResponse);
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private EnrollmentResponse mapToResponse(Enrollment e) {
        String enrolledByName = (e.getEnrolledBy() != null)
            ? e.getEnrolledBy().getFullName()
            : "System — Payment";

        return EnrollmentResponse.builder()
            .id(e.getId())
            .studentId(e.getStudent().getId())
            .studentName(e.getStudent().getFullName())
            .studentEmail(e.getStudent().getEmail())
            .studentPhotoUrl(e.getStudent().getProfilePhotoUrl())
            .courseId(e.getCourse().getId())
            .courseTitle(e.getCourse().getTitle())
            .status(e.getStatus().name())
            .enrolledByName(enrolledByName)
            .paymentReference(e.getPaymentReference())
            .enrolledAt(e.getEnrolledAt())
            .build();
    }
}
