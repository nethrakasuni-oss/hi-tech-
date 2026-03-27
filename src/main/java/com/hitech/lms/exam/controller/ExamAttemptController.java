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
import com.hitech.lms.exam.service.ExamAttemptService;
import com.hitech.lms.exam.service.ExamGradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ExamAttemptController — exam taking + grading endpoints.
 * Base path: /api/exam-attempts
 *
 * FR-5.2: start, auto-save, submit
 * FR-5.3: grading queue, grade question
 * FR-5.4: view results
 */
@RestController
@RequestMapping("/api/exam-attempts")
public class ExamAttemptController {

    @Autowired private ExamAttemptService attemptService;
    @Autowired private ExamGradingService gradingService;

    // ---- POST /api/exam-attempts/start/{examId} — Student starts / resumes attempt ----
    @PostMapping("/start/{examId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AttemptResponse>> startAttempt(
            @PathVariable Long examId,
            @AuthenticationPrincipal User student) {

        AttemptResponse resp = attemptService.startAttempt(examId, student);
        String msg = resp.getStatus().equals("IN_PROGRESS") ? "Attempt started." : "Attempt resumed.";
        return ResponseEntity.ok(ApiResponse.success(msg, resp));
    }

    // ---- PATCH /api/exam-attempts/{attemptId}/autosave — Auto-save answers ----
    @PatchMapping("/{attemptId}/autosave")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> autoSave(
            @PathVariable Long attemptId,
            @RequestBody AutoSaveRequest request,
            @AuthenticationPrincipal User student) {

        attemptService.autoSave(attemptId, request, student);
        return ResponseEntity.ok(ApiResponse.success("Answers saved."));
    }

    // ---- POST /api/exam-attempts/{attemptId}/submit — Final submission ----
    @PostMapping("/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> submitAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal User student) {

        AttemptResultResponse result = attemptService.submitAttempt(attemptId, student);
        return ResponseEntity.ok(ApiResponse.success("Exam submitted.", result));
    }

    // ---- GET /api/exam-attempts/my-results — Student: all released result summaries ----
    @GetMapping("/my-results")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<java.util.List<StudentResultSummaryDto>>> getMyResults(
            @AuthenticationPrincipal User student) {

        return ResponseEntity.ok(ApiResponse.success("Results fetched.",
            attemptService.getMyResults(student)));
    }

    // ---- GET /api/exam-attempts/{attemptId}/results — View results ----
    @GetMapping("/{attemptId}/results")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> getResults(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal User viewer) {

        return ResponseEntity.ok(ApiResponse.success("Results fetched.",
            attemptService.getResults(attemptId, viewer)));
    }

    // ---- GET /api/exam-attempts/grade/{examId} — Grading queue ----
    @GetMapping("/grade/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<GradingQueueResponse>> getGradingQueue(
            @PathVariable Long examId,
            @AuthenticationPrincipal User grader) {

        return ResponseEntity.ok(ApiResponse.success("Grading queue fetched.",
            gradingService.getGradingQueue(examId, grader)));
    }

    // ---- POST /api/exam-attempts/{attemptId}/grade/{questionId} — Grade one question ----
    @PostMapping("/{attemptId}/grade/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> gradeQuestion(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @RequestBody GradeSubmissionRequest request,
            @AuthenticationPrincipal User grader) {

        gradingService.gradeQuestion(attemptId, questionId, request, grader);
        return ResponseEntity.ok(ApiResponse.success("Grade saved."));
    }
}
