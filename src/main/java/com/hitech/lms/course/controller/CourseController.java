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

import java.util.List;

/**
 * CourseController — Course endpoints for all authenticated users.
 *
 * Base path: /api/courses
 *
 * Covers:
 * - GET: Course catalog (Student), course list (Instructor/Admin)
 * - GET: Course detail page
 * - POST/PATCH: Create and update (Admin/Instructor)
 * - POST: Publish, Archive, Delete lifecycle transitions
 * - GET/POST/DELETE: Content tree management (Admin/Instructor)
 * - GET/POST/DELETE: Material management (Admin/Instructor)
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired private CourseService      courseService;
    @Autowired private ContentService     contentService;
    @Autowired private EnrollmentService  enrollmentService;
    @Autowired private AttendanceService  attendanceService;

    // =====================================================
    // COURSE LISTING
    // =====================================================

    /**
     * GET /api/courses/catalog
     * Student: Browse all ACTIVE courses
     * FR-2.3
     */
    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getCourseCatalog(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long programId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<CourseSummaryResponse> courses = courseService.getCourseCatalog(search, programId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Courses fetched.", courses));
    }

    /**
     * GET /api/courses/my
     * Student: Get their enrolled courses
     * FR-2.3
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<CourseSummaryResponse>>> getMyEnrolledCourses(
            @AuthenticationPrincipal User currentUser) {

        List<CourseSummaryResponse> courses = courseService.getMyEnrolledCourses(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Enrolled courses fetched.", courses));
    }

    /**
     * GET /api/courses/manage
     * Admin: all courses with all statuses
     * Instructor: only their assigned courses
     * FR-2.1
     */
    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getManageCourses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long programId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser) {

        Page<CourseSummaryResponse> courses;
        if (currentUser.getRole() == User.Role.ADMIN) {
            courses = courseService.getCoursesForAdmin(search, status, programId, page, size);
        } else {
            courses = courseService.getCoursesForInstructor(currentUser, search, status, page, size);
        }
        return ResponseEntity.ok(ApiResponse.success("Courses fetched.", courses));
    }

    /**
     * GET /api/courses/programs
     * All roles: list of programs for dropdown
     */
    @GetMapping("/programs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProgramResponse>>> getPrograms() {
        return ResponseEntity.ok(
            ApiResponse.success("Programs fetched.", courseService.getAllPrograms())
        );
    }

    // =====================================================
    // SINGLE COURSE DETAIL
    // =====================================================

    /**
     * GET /api/courses/{id}
     * Returns full course details.
     * Students only see ACTIVE courses.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
            ApiResponse.success("Course fetched.", courseService.getCourse(id, currentUser))
        );
    }

    // =====================================================
    // COURSE CREATION & MANAGEMENT
    // FR-2.1
    // =====================================================

    /**
     * POST /api/courses
     * Admin or Instructor creates a new course (DRAFT)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal User currentUser) {

        CourseDetailResponse created = courseService.createCourse(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Course created successfully.", created));
    }

    /**
     * PATCH /api/courses/{id}
     * Edit an existing course
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal User currentUser) {

        CourseDetailResponse res = courseService.updateCourse(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", res));
    }

    /**
     * PATCH /api/courses/{id}/progress
     * Only Instructor: update current week/day progress
     */
    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> updateCourseProgress(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseProgressRequest request,
            @AuthenticationPrincipal User currentUser) {

        CourseDetailResponse res = courseService.updateCourseProgress(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course progress updated successfully", res));
    }

    /**
     * POST /api/courses/{id}/publish
     * Validates and moves DRAFT -> ACTIVE
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> publishCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
            ApiResponse.success("Course published successfully!", courseService.publishCourse(id, currentUser))
        );
    }

    /**
     * POST /api/courses/{id}/archive
     * Transition ACTIVE → ARCHIVED
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> archiveCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
            ApiResponse.success("Course archived.", courseService.archiveCourse(id, currentUser))
        );
    }

    /**
     * DELETE /api/courses/{id}
     * Delete a DRAFT course with zero enrollments
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        courseService.deleteCourse(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Course deleted."));
    }

    // =====================================================
    // CONTENT TREE MANAGEMENT
    // FR-2.2
    // =====================================================

    /**
     * GET /api/courses/{id}/content
     * Returns the full Week → Module → Topic tree with materials
     */
    @GetMapping("/{id}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContentNodeResponse>>> getContentTree(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        // Students must be enrolled to view content (FR-2.3)
        if (currentUser.getRole() == User.Role.STUDENT) {
            enrollmentService.verifyEnrollmentAccess(id, currentUser.getId());
        }

        return ResponseEntity.ok(
            ApiResponse.success("Content tree fetched.", contentService.getContentTree(id))
        );
    }

    /**
     * POST /api/courses/{id}/content-nodes
     * Create a Week, Module, or Topic node
     */
    @PostMapping("/{id}/content-nodes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentNodeResponse>> createNode(
            @PathVariable Long id,
            @Valid @RequestBody CreateContentNodeRequest request,
            @AuthenticationPrincipal User currentUser) {

        ContentNodeResponse node = contentService.createNode(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Content node created.", node));
    }

    /**
     * PATCH /api/courses/{courseId}/content-nodes/{nodeId}
     * Rename or reorder a node
     */
    @PatchMapping("/{courseId}/content-nodes/{nodeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentNodeResponse>> updateNode(
            @PathVariable Long courseId,
            @PathVariable Long nodeId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer sortOrder,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
            ApiResponse.success("Node updated.", contentService.updateNode(nodeId, title, sortOrder, currentUser))
        );
    }

    /**
     * DELETE /api/courses/{courseId}/content-nodes/{nodeId}
     * Delete a node and all its children/materials
     */
    @DeleteMapping("/{courseId}/content-nodes/{nodeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteNode(
            @PathVariable Long courseId,
            @PathVariable Long nodeId,
            @AuthenticationPrincipal User currentUser) {

        contentService.deleteNode(nodeId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Node deleted."));
    }

    // =====================================================
    // MATERIAL MANAGEMENT
    // FR-2.2
    // =====================================================

    /**
     * POST /api/courses/{id}/materials
     * Upload a file or add a link to a course node
     */
    @PostMapping("/{id}/materials")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<MaterialResponse>> addMaterial(
            @PathVariable Long id,
            @Valid @RequestBody AddMaterialRequest request,
            @AuthenticationPrincipal User currentUser) {

        MaterialResponse material = contentService.addMaterial(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Material added.", material));
    }

    /**
     * DELETE /api/courses/{courseId}/materials/{materialId}
     */
    @DeleteMapping("/{courseId}/materials/{materialId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(
            @PathVariable Long courseId,
            @PathVariable Long materialId,
            @AuthenticationPrincipal User currentUser) {

        contentService.deleteMaterial(materialId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Material deleted."));
    }

    // =====================================================
    // ATTENDANCE MANAGEMENT
    // =====================================================

    /**
     * POST /api/courses/{id}/attendance/import
     * Instructor imports daily attendance via CSV content.
     * Upserts: updates existing records when re-importing the same date.
     *
     * Expects JSON body: { "csvContent": "...", "date": "YYYY-MM-DD" }
     */
    @PostMapping("/{id}/attendance/import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AttendanceImportResult>> importAttendance(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> request,
            @AuthenticationPrincipal User currentUser) {

        String csvContent = request.get("csvContent");
        String date = request.get("date");

        if (csvContent == null || csvContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("CSV content is required."));
        }
        if (date == null || date.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Date is required."));
        }

        AttendanceImportResult result = attendanceService.importAttendance(id, csvContent, date, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Attendance imported successfully.", result));
    }

    /**
     * GET /api/courses/{id}/attendance?date=YYYY-MM-DD
     * Get attendance records for a specific date.
     */
    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getAttendance(
            @PathVariable Long id,
            @RequestParam String date,
            @AuthenticationPrincipal User currentUser) {

        List<AttendanceResponse> records = attendanceService.getAttendanceByDate(id, date, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Attendance fetched.", records));
    }

    /**
     * GET /api/courses/{id}/attendance/dates
     * List distinct dates for which attendance has been recorded.
     */
    @GetMapping("/{id}/attendance/dates")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<java.time.LocalDate>>> getAttendanceDates(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        List<java.time.LocalDate> dates = attendanceService.getAttendanceDates(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Attendance dates fetched.", dates));
    }
}
