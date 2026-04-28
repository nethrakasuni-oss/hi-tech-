package com.hitech.lms.exam.controller;
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
import com.hitech.lms.exam.service.ExamService;
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
 * ExamController — REST endpoints for exam CRUD, lifecycle, analytics.
 * Base path: /api/exams
 *
 * RBAC:
 *   Admin/Instructor  — full exam management
 *   Student           — view active exams for enrolled courses
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    @Autowired private ExamService examService;

    // ---- GET /api/exams/manage — Instructor/Admin exam list ----
    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Page<ExamSummaryResponse>>> getExamsForManagement(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(ApiResponse.success("Exams fetched.",
            examService.getExamsForManagement(viewer, courseId, status, search, page, size)));
    }

    // ---- GET /api/exams — Student: active exams for enrolled courses ----
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamSummaryResponse>>> getStudentExams(
            @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal User student) {

        return ResponseEntity.ok(ApiResponse.success("Exams fetched.",
            examService.getExamsForStudent(student, courseId)));
    }

    // ---- GET /api/exams/{id} — Instructor/Admin: full exam detail with questions ----
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamDetailResponse>> getExamDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(ApiResponse.success("Exam fetched.",
            examService.getExamDetail(id, viewer)));
    }

    // ---- POST /api/exams — Create exam (DRAFT) ----
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamDetailResponse>> createExam(
            @Valid @RequestBody CreateExamRequest request,
            @AuthenticationPrincipal User creator) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Exam created.", examService.createExam(request, creator)));
    }

    // ---- PUT /api/exams/{id} — Update exam (DRAFT only) ----
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamDetailResponse>> updateExam(
            @PathVariable Long id,
            @Valid @RequestBody CreateExamRequest request,
            @AuthenticationPrincipal User editor) {

        return ResponseEntity.ok(ApiResponse.success("Exam updated.",
            examService.updateExam(id, request, editor)));
    }

    // ---- POST /api/exams/{id}/publish — DRAFT → ACTIVE ----
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamSummaryResponse>> publishExam(
            @PathVariable Long id,
            @AuthenticationPrincipal User publisher) {

        return ResponseEntity.ok(ApiResponse.success("Exam published.",
            examService.publishExam(id, publisher)));
    }

    // ---- POST /api/exams/{id}/deactivate ----
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamSummaryResponse>> deactivateExam(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        return ResponseEntity.ok(ApiResponse.success("Exam deactivated.",
            examService.deactivateExam(id, actor)));
    }

    // ---- POST /api/exams/{id}/archive ----
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamSummaryResponse>> archiveExam(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        return ResponseEntity.ok(ApiResponse.success("Exam archived.",
            examService.archiveExam(id, actor)));
    }

    // ---- DELETE /api/exams/{id} — DRAFT only ----
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteExam(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        examService.deleteExam(id, actor);
        return ResponseEntity.ok(ApiResponse.success("Exam deleted."));
    }

    // ---- POST /api/exams/{id}/release-results ----
    @PostMapping("/{id}/release-results")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamSummaryResponse>> releaseResults(
            @PathVariable Long id,
            @AuthenticationPrincipal User actor) {

        return ResponseEntity.ok(ApiResponse.success("Results released.",
            examService.releaseResults(id, actor)));
    }

    // ---- GET /api/exams/{id}/analytics ----
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamAnalyticsResponse>> getAnalytics(
            @PathVariable Long id,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(ApiResponse.success("Analytics fetched.",
            examService.getAnalytics(id, viewer)));
    }
}
