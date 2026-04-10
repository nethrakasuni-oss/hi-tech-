package com.hitech.lms.schedule.controller;
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
import com.hitech.lms.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ScheduleController — REST API for FR-4.1 Class Scheduling.
 * Base path: /api/schedule
 *
 * Access rules:
 *   - Admin: full CRUD
 *   - Instructor: read (own sessions)
 *   - Student: read (enrolled course sessions)
 */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    // ---- GET /api/schedule ----
    // Calendar sessions — role-aware, filtered by date range and optional course
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal User currentUser) {

        // Default to current month if no range provided
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end   = to   != null ? to   : start.plusMonths(1).minusDays(1);

        List<SessionResponse> sessions =
            scheduleService.getCalendarSessions(currentUser, start, end, courseId);

        return ResponseEntity.ok(ApiResponse.success("Sessions fetched.", sessions));
    }

    // ---- GET /api/schedule/upcoming ----
    // Next 5 upcoming sessions for the sidebar panel and dashboard widget
    @GetMapping("/upcoming")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UpcomingSessionsResponse>>> getUpcoming(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.success("Upcoming sessions fetched.",
            scheduleService.getUpcomingSessions(currentUser, limit)));
    }

    // ---- GET /api/schedule/{id} ----
    // Single session detail
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.success("Session fetched.",
            scheduleService.getSession(id, currentUser)));
    }

    // ---- POST /api/schedule ----
    // Create session(s) — Admin only
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal User admin) {

        List<SessionResponse> created = scheduleService.createSession(request, admin);
        String msg = created.size() == 1
            ? "Session created successfully."
            : created.size() + " recurring sessions created successfully.";

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(msg, created));
    }

    // ---- PATCH /api/schedule/{id} ----
    // Update session — Admin only
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal User admin) {

        List<SessionResponse> updated = scheduleService.updateSession(id, request, admin);
        return ResponseEntity.ok(ApiResponse.success("Session(s) updated.", updated));
    }

    // ---- POST /api/schedule/{id}/cancel ----
    // Cancel session(s) — Admin and Instructor
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
            @PathVariable Long id,
            @RequestParam(defaultValue = "THIS_ONLY") String editScope,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.success("Session(s) cancelled.",
            scheduleService.cancelSession(id, editScope, currentUser)));
    }

    // ---- DELETE /api/schedule/{id} ----
    // Delete session(s) — Admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable Long id,
            @RequestParam(defaultValue = "THIS_ONLY") String editScope,
            @AuthenticationPrincipal User admin) {

        scheduleService.deleteSession(id, editScope, admin);
        return ResponseEntity.ok(ApiResponse.success("Session(s) deleted."));
    }

    // ---- POST /api/schedule/check-conflict ----
    // Check instructor availability — Admin only (called before form submit)
    @PostMapping("/check-conflict")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConflictCheckResponse>> checkConflict(
            @Valid @RequestBody ConflictCheckRequest request,
            @AuthenticationPrincipal User admin) {

        ConflictCheckResponse result = scheduleService.checkConflict(
            request.getInstructorId(), request.getSessionDate(),
            request.getStartTime(), request.getEndTime(),
            request.getExcludeSessionId());

        return ResponseEntity.ok(ApiResponse.success("Conflict check complete.", result));
    }

    // ---- GET /api/schedule/dashboard-stats ----
    // Used by dashboard to show "Upcoming Sessions" count
    @GetMapping("/dashboard-stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> getDashboardStats(
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.success("Stats fetched.",
            scheduleService.countUpcomingThisWeek(currentUser)));
    }
}
