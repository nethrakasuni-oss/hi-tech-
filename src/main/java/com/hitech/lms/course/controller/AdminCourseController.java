package com.hitech.lms.course.controller;

import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.auth.service.*;
import com.hitech.lms.user.service.*;
import com.hitech.lms.course.service.*;
import com.hitech.lms.exam.service.*;
import com.hitech.lms.schedule.service.*;
import com.hitech.lms.finance.service.*;
import com.hitech.lms.support.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AdminCourseController — Admin-only endpoints for enrollment management.
 *
 * Base path: /api/admin/courses
 *
 * FR-2.3: Enrollment Management & Access Control
 * - Admin manually enrolls and removes students
 * - Admin views all enrollments for a course
 * - Admin course stats for dashboard
 */
@RestController
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService     courseService;

    // =====================================================
    // ENROLLMENT MANAGEMENT
    // FR-2.3
    // =====================================================

    /**
     * GET /api/admin/courses/{courseId}/enrollments
     * Paginated, searchable list of enrollments for a course
     */
    @GetMapping("/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> getEnrollments(
            @PathVariable Long courseId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<EnrollmentResponse> enrollments = enrollmentService.getEnrollmentsForCourse(
            courseId, search, status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Enrollments fetched.", enrollments));
    }

    /**
     * POST /api/admin/courses/{courseId}/enrollments
     * Admin manually enrolls a student
     * FR-2.3 Step 63
     */
    @PostMapping("/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enrollStudent(
            @PathVariable Long courseId,
            @Valid @RequestBody EnrollStudentRequest request,
            @AuthenticationPrincipal User adminUser) {

        EnrollmentResponse enrollment = enrollmentService.enrollStudent(courseId, request, adminUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                "Student enrolled successfully. They now have access to all published course content.",
                enrollment
            ));
    }

    /**
     * DELETE /api/admin/courses/{courseId}/enrollments/{enrollmentId}
     * Remove a student's enrollment (data preserved, access revoked)
     * FR-2.3 Step 68
     */
    @DeleteMapping("/{courseId}/enrollments/{enrollmentId}")
    public ResponseEntity<ApiResponse<Void>> removeEnrollment(
            @PathVariable Long courseId,
            @PathVariable Long enrollmentId,
            @AuthenticationPrincipal User adminUser) {

        enrollmentService.removeEnrollment(courseId, enrollmentId, adminUser);
        return ResponseEntity.ok(
            ApiResponse.success("Enrollment removed. Student's historical data is preserved.")
        );
    }

    // =====================================================
    // COURSE STATS FOR DASHBOARD
    // =====================================================

    /**
     * GET /api/admin/courses/stats
     * Returns course KPI counts for Admin Dashboard
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CourseStatsResponse>> getCourseStats() {
        return ResponseEntity.ok(
            ApiResponse.success("Course stats fetched.", courseService.getCourseStats())
        );
    }
}
