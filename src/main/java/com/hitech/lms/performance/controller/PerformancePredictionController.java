package com.hitech.lms.performance.controller;

import com.hitech.lms.auth.model.User;
import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.performance.dto.PerformancePredictionResponse;
import com.hitech.lms.performance.service.PerformancePredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performance")
public class PerformancePredictionController {

    @Autowired private PerformancePredictionService predictionService;

    @GetMapping("/courses/{courseId}/predictions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<PerformancePredictionResponse>> predictCoursePerformance(
            @PathVariable Long courseId,
            @AuthenticationPrincipal User currentUser) {

        PerformancePredictionResponse response = predictionService.predictCoursePerformance(courseId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Performance predictions generated.", response));
    }
}
