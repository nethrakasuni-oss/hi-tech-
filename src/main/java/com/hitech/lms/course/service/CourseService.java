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

import java.util.List;
import java.util.stream.Collectors;

/**
 * CourseService — Business logic for FR-2.1 (Course Creation & Management).
 *
 * Handles:
 * - Course CRUD (Create, Read, Update lifecycle transitions)
 * - Instructor assignment
 * - Course catalog browsing for students
 * - Course listing for admins and instructors
 */
@Service
@Transactional
public class CourseService {

    private static final Logger logger = LoggerFactory.getLogger(CourseService.class);

    @Autowired private CourseRepository      courseRepository;
    @Autowired private ProgramRepository     programRepository;
    @Autowired private UserRepository        userRepository;
    @Autowired private EnrollmentRepository  enrollmentRepository;

    // =====================================================
    // CREATE COURSE (Admin / Instructor)
    // FR-2.1 Step 1: Course created in DRAFT status
    // =====================================================

    public CourseDetailResponse createCourse(CreateCourseRequest request, User creator) {

        // Validate course code uniqueness (FR-2.1)
        if (courseRepository.existsByCourseCode(request.getCourseCode().toUpperCase())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This course code already exists. Please use a unique code."
            );
        }

        Program program = programRepository.findById(request.getProgramId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Selected program does not exist."
            ));

        Course course = Course.builder()
            .title(request.getTitle().trim())
            .courseCode(request.getCourseCode().toUpperCase().trim())
            .description(request.getDescription())
            .program(program)
            .durationValue(request.getDurationValue())
            .durationUnit(request.getDurationUnit())
            .courseFee(request.getCourseFee())
            .enrollmentType(request.getEnrollmentType() != null
                ? request.getEnrollmentType() : Course.EnrollmentType.OPEN)
            .maxStudents(request.getMaxStudents())
            .thumbnailUrl(request.getThumbnailUrl())
            .status(Course.CourseStatus.DRAFT)
            .createdBy(creator)
            .build();

        // Assign instructors if provided
        if (request.getInstructorIds() != null && !request.getInstructorIds().isEmpty()) {
            List<User> instructors = userRepository.findAllById(request.getInstructorIds());
            // Validate all are INSTRUCTOR role
            instructors.stream()
                .filter(u -> u.getRole() != User.Role.INSTRUCTOR && u.getRole() != User.Role.ADMIN)
                .findFirst()
                .ifPresent(u -> { throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User " + u.getFullName() + " is not an Instructor.");
                });
            course.setInstructors(instructors);
        }

        course = courseRepository.save(course);
        logger.info("Course created: {} ({}) by {}", course.getTitle(), course.getCourseCode(), creator.getEmail());

        return mapToDetailResponse(course, null);
    }

    // =====================================================
    // UPDATE COURSE
    // FR-2.1: Admins and assigned Instructors can edit
    // =====================================================

    public CourseDetailResponse updateCourse(Long courseId, UpdateCourseRequest request, User updater) {
        Course course = findCourseById(courseId);

        // Archived courses cannot be edited
        if (course.getStatus() == Course.CourseStatus.ARCHIVED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Archived courses cannot be edited. Create a copy instead."
            );
        }

        // Instructor can only edit their assigned courses
        if (updater.getRole() == User.Role.INSTRUCTOR
                && course.getInstructors().stream().noneMatch(i -> i.getId().equals(updater.getId()))) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You can only edit courses assigned to you."
            );
        }

        if (request.getTitle() != null) course.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getDurationValue() != null) course.setDurationValue(request.getDurationValue());
        if (request.getDurationUnit() != null) course.setDurationUnit(request.getDurationUnit());
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getEnrollmentType() != null) course.setEnrollmentType(request.getEnrollmentType());
        if (request.getMaxStudents() != null) course.setMaxStudents(request.getMaxStudents());

        // Fee change warning: if students already enrolled, log a warning but allow
        if (request.getCourseFee() != null) {
            long enrolled = enrollmentRepository.countByCourseIdAndStatus(
                courseId, Enrollment.EnrollmentStatus.ACTIVE);
            if (enrolled > 0) {
                logger.warn("Course fee changed on course {} which has {} active enrollments",
                    courseId, enrolled);
            }
            course.setCourseFee(request.getCourseFee());
        }

        if (request.getProgramId() != null) {
            Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Selected program does not exist."));
            course.setProgram(program);
        }

        // Admin-only: reassign instructors
        if (request.getInstructorIds() != null && updater.getRole() == User.Role.ADMIN) {
            List<User> instructors = userRepository.findAllById(request.getInstructorIds());
            course.setInstructors(instructors);
        }

        courseRepository.save(course);
        return mapToDetailResponse(course, null);
    }

    // =====================================================
    // UPDATE COURSE PROGRESS
    // =====================================================

    public CourseDetailResponse updateCourseProgress(Long courseId, UpdateCourseProgressRequest request, User updater) {
        Course course = findCourseById(courseId);

        if (updater.getRole() == User.Role.INSTRUCTOR
                && course.getInstructors().stream().noneMatch(i -> i.getId().equals(updater.getId()))) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You can only update progress on courses assigned to you."
            );
        }

        course.setCurrentProgress(request.getCurrentProgress());
        courseRepository.save(course);
        return mapToDetailResponse(course, null);
    }

    // =====================================================
    // PUBLISH COURSE (DRAFT → ACTIVE)
    // FR-2.1: Validates all rules before publishing
    // =====================================================

    public CourseDetailResponse publishCourse(Long courseId, User publisher) {
        Course course = findCourseById(courseId);

        if (course.getStatus() != Course.CourseStatus.DRAFT) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only DRAFT courses can be published."
            );
        }

        // FR-2.1: At least one instructor must be assigned before publishing
        if (course.getInstructors().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "At least one instructor must be assigned before publishing."
            );
        }

        course.setStatus(Course.CourseStatus.ACTIVE);
        courseRepository.save(course);
        logger.info("Course published: {} by {}", course.getCourseCode(), publisher.getEmail());

        return mapToDetailResponse(course, null);
    }

    // =====================================================
    // ARCHIVE COURSE (ACTIVE → ARCHIVED)
    // FR-2.1: Archived courses are read-only
    // =====================================================

    public CourseDetailResponse archiveCourse(Long courseId, User archiver) {
        Course course = findCourseById(courseId);

        if (course.getStatus() == Course.CourseStatus.ARCHIVED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "This course is already archived."
            );
        }

        // FR-2.1: DRAFT courses with zero enrollments can be deleted
        // ACTIVE courses should be archived (data preserved)
        course.setStatus(Course.CourseStatus.ARCHIVED);
        courseRepository.save(course);
        logger.info("Course archived: {} by {}", course.getCourseCode(), archiver.getEmail());

        return mapToDetailResponse(course, null);
    }

    // =====================================================
    // DELETE COURSE (DRAFT only, zero enrollments)
    // FR-2.1: Only DRAFT with zero attempts can be deleted
    // =====================================================

    public void deleteCourse(Long courseId, User deleter) {
        Course course = findCourseById(courseId);

        if (course.getStatus() != Course.CourseStatus.DRAFT) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only DRAFT courses can be deleted. Archive this course instead to preserve student records."
            );
        }

        long enrollments = enrollmentRepository.countByCourseIdAndStatus(
            courseId, Enrollment.EnrollmentStatus.ACTIVE);
        if (enrollments > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This course has active enrollments and cannot be deleted. Archive it instead."
            );
        }

        courseRepository.delete(course);
        logger.info("Course deleted: {} by {}", courseId, deleter.getEmail());
    }

    // =====================================================
    // GET SINGLE COURSE
    // =====================================================

    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(Long courseId, User viewer) {
        Course course = findCourseById(courseId);

        // Students can only see ACTIVE courses
        if (viewer.getRole() == User.Role.STUDENT
                && course.getStatus() != Course.CourseStatus.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Course not found."
            );
        }

        return mapToDetailResponse(course, viewer);
    }

    // =====================================================
    // GET COURSES FOR ADMIN (paginated, all statuses)
    // =====================================================

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getCoursesForAdmin(
            String search, String status, Long programId, int page, int size) {

        Course.CourseStatus statusEnum = (status != null && !status.isBlank())
            ? Course.CourseStatus.valueOf(status) : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return courseRepository.searchCourses(search, statusEnum, programId, pageable)
            .map(c -> mapToSummaryResponse(c));
    }

    // =====================================================
    // GET COURSES FOR INSTRUCTOR (only assigned courses)
    // =====================================================

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getCoursesForInstructor(
            User instructor, String search, String status, int page, int size) {

        Course.CourseStatus statusEnum = (status != null && !status.isBlank())
            ? Course.CourseStatus.valueOf(status) : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return courseRepository.searchCoursesByInstructor(instructor, search, statusEnum, pageable)
            .map(c -> mapToSummaryResponse(c));
    }

    // =====================================================
    // GET COURSE CATALOG FOR STUDENTS (ACTIVE only)
    // FR-2.3: Students browse active enrollable courses
    // =====================================================

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getCourseCatalog(
            String search, Long programId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return courseRepository.searchActiveCourses(search, programId, pageable)
            .map(c -> mapToSummaryResponse(c));
    }

    // =====================================================
    // GET MY ENROLLED COURSES (for Student dashboard)
    // FR-2.3
    // =====================================================

    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> getMyEnrolledCourses(Long studentId) {
        return courseRepository.findEnrolledCoursesByStudent(studentId)
            .stream()
            .map(c -> mapToSummaryResponse(c))
            .collect(Collectors.toList());
    }

    // =====================================================
    // COURSE STATS (for Admin Dashboard)
    // =====================================================

    @Transactional(readOnly = true)
    public CourseStatsResponse getCourseStats() {
        return CourseStatsResponse.builder()
            .totalCourses(courseRepository.count())
            .activeCourses(courseRepository.countByStatus(Course.CourseStatus.ACTIVE))
            .draftCourses(courseRepository.countByStatus(Course.CourseStatus.DRAFT))
            .archivedCourses(courseRepository.countByStatus(Course.CourseStatus.ARCHIVED))
            .totalEnrollments(enrollmentRepository.count())
            .build();
    }

    // =====================================================
    // GET ALL PROGRAMS (for dropdowns)
    // =====================================================

    @Transactional(readOnly = true)
    public List<ProgramResponse> getAllPrograms() {
        return programRepository.findByIsActiveTrueOrderByNameAsc()
            .stream()
            .map(p -> ProgramResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .isActive(p.getIsActive())
                .build())
            .collect(Collectors.toList());
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    public Course findCourseById(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Course not found."
            ));
    }

    private CourseDetailResponse mapToDetailResponse(Course course, User viewer) {
        long enrolledCount = enrollmentRepository.countByCourseIdAndStatus(
            course.getId(), Enrollment.EnrollmentStatus.ACTIVE);

        boolean isEnrolled = false;
        if (viewer != null && viewer.getRole() == User.Role.STUDENT) {
            isEnrolled = enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                viewer.getId(), course.getId(), Enrollment.EnrollmentStatus.ACTIVE);
        }

        List<CourseDetailResponse.InstructorSummary> instructorSummaries =
            course.getInstructors().stream()
                .map(i -> CourseDetailResponse.InstructorSummary.builder()
                    .id(i.getId())
                    .fullName(i.getFullName())
                    .email(i.getEmail())
                    .profilePhotoUrl(i.getProfilePhotoUrl())
                    .build())
                .collect(Collectors.toList());

        return CourseDetailResponse.builder()
            .id(course.getId())
            .title(course.getTitle())
            .courseCode(course.getCourseCode())
            .description(course.getDescription())
            .programId(course.getProgram().getId())
            .programName(course.getProgram().getName())
            .durationValue(course.getDurationValue())
            .durationUnit(course.getDurationUnit())
            .courseFee(course.getCourseFee())
            .enrollmentType(course.getEnrollmentType().name())
            .maxStudents(course.getMaxStudents())
            .thumbnailUrl(course.getThumbnailUrl())
            .currentProgress(course.getCurrentProgress())
            .status(course.getStatus().name())
            .enrolledCount(enrolledCount)
            .isEnrolled(isEnrolled)
            .instructors(instructorSummaries)
            .createdById(course.getCreatedBy().getId())
            .createdByName(course.getCreatedBy().getFullName())
            .createdAt(course.getCreatedAt())
            .updatedAt(course.getUpdatedAt())
            .build();
    }

    private CourseSummaryResponse mapToSummaryResponse(Course course) {
        long enrolledCount = enrollmentRepository.countByCourseIdAndStatus(
            course.getId(), Enrollment.EnrollmentStatus.ACTIVE);

        String primaryInstructor = course.getInstructors().isEmpty()
            ? null
            : course.getInstructors().get(0).getFullName();

        return CourseSummaryResponse.builder()
            .id(course.getId())
            .title(course.getTitle())
            .courseCode(course.getCourseCode())
            .programName(course.getProgram().getName())
            .durationValue(course.getDurationValue() != null ? String.valueOf(course.getDurationValue()) : null)
            .durationUnit(course.getDurationUnit())
            .courseFee(course.getCourseFee())
            .status(course.getStatus().name())
            .thumbnailUrl(course.getThumbnailUrl())
            .enrolledCount(enrolledCount)
            .enrollmentType(course.getEnrollmentType().name())
            .primaryInstructorName(primaryInstructor)
            .createdAt(course.getCreatedAt())
            .build();
    }
}
