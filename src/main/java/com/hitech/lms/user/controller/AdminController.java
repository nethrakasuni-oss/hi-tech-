package com.hitech.lms.user.controller;
import com.hitech.lms.user.dto.*;





import com.hitech.lms.dto.ApiResponse;

import com.hitech.lms.auth.model.User;
import com.hitech.lms.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.hitech.lms.course.dto.CourseStatsResponse;
import com.hitech.lms.course.service.CourseService;
import com.hitech.lms.finance.dto.FinanceDashboardResponse;
import com.hitech.lms.finance.service.PaymentService;

/**
 * AdminController — Admin-only endpoints for user management.
 *
 * @PreAuthorize("hasRole('ADMIN')") is a METHOD-LEVEL security check.
 * Even if someone bypasses the URL pattern in SecurityConfig,
 * this annotation blocks them at the method level. Double protection.
 *
 * Base path: /api/admin
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")  // All methods in this controller require ADMIN role
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private PaymentService paymentService;

    // ---- GET /api/admin/users ----
    // FR-1.2: Paginated, searchable, filterable user list for Admin User Management screen
    // Query params: search, role, status, page, size
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<UserSummaryResponse> users = userService.getUsers(
                search, role, status, page, size, "createdAt"
        );

        return ResponseEntity.ok(
                ApiResponse.success("Users fetched.", users)
        );
    }

    // ---- GET /api/admin/users/{id} ----
    // Returns full profile for a specific user (Admin can view any profile)
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("User fetched.", userService.adminGetUser(id))
        );
    }

    // ---- POST /api/admin/users ----
    // FR-1.1: Admin creates Instructor or Admin accounts
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal User adminUser) {

        UserProfileResponse created = userService.adminCreateUser(request, adminUser);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "User account created successfully. A welcome email has been sent.", created
                ));
    }

    // ---- POST /api/admin/users/bulk-import ----
    @PostMapping(value = "/users/bulk-import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<com.hitech.lms.user.dto.BulkImportSummaryResponse>> bulkImportUsers(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal User adminUser) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty."));
        }
        
        com.hitech.lms.user.dto.BulkImportSummaryResponse summary = userService.bulkImportUsers(file, adminUser);
        
        return ResponseEntity.ok(ApiResponse.success("Import processed.", summary));
    }

    // ---- PATCH /api/admin/users/{id} ----
    // Admin edits a user's information
    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal User adminUser) {

        UserProfileResponse updated = userService.adminUpdateUser(id, request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully.", updated));
    }

    // ---- POST /api/admin/users/{id}/deactivate ----
    // FR-1.4: Deactivate a user account
    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean sendEmail,
            @AuthenticationPrincipal User adminUser) {

        userService.deactivateUser(id, adminUser, sendEmail);

        return ResponseEntity.ok(
                ApiResponse.success("Account has been deactivated. Active sessions will be terminated.")
        );
    }

    // ---- POST /api/admin/users/{id}/reactivate ----
    // FR-1.4: Reactivate a previously deactivated account
    @PostMapping("/users/{id}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User adminUser) {

        userService.reactivateUser(id, adminUser);

        return ResponseEntity.ok(
                ApiResponse.success("Account has been reactivated successfully.")
        );
    }

    // ---- GET /api/admin/dashboard/stats ----
// Returns user count KPIs for the Admin Dashboard
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getDashboardStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Stats fetched.", userService.getUserStats())
        );
    }

    // ---- GET /api/admin/dashboard/course-stats ----  ← NEW method to ADD
// Returns course KPI counts for the Admin Dashboard
    @GetMapping("/dashboard/course-stats")
    public ResponseEntity<ApiResponse<CourseStatsResponse>> getCourseDashboardStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Course stats fetched.", courseService.getCourseStats())
        );
    }

    @GetMapping("/dashboard/finance-stats")
    public ResponseEntity<ApiResponse<FinanceDashboardResponse>> getFinanceDashboardStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Finance stats fetched.", paymentService.getDashboardStats()));
    }
}