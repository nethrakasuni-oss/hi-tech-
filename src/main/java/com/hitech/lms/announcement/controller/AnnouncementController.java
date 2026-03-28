package com.hitech.lms.announcement.controller;
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


import com.hitech.lms.announcement.dto.AnnouncementResponse;
import com.hitech.lms.announcement.dto.CreateAnnouncementRequest;
import com.hitech.lms.announcement.service.AnnouncementService;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AnnouncementController — REST API for the Announcements feature.
 *
 * Routes:
 *  POST   /api/announcements              → Create (Admin or Instructor)
 *  GET    /api/announcements/system       → Get system-wide announcements (all authenticated)
 *  GET    /api/announcements/course/{id}  → Get course announcements (all authenticated)
 *  GET    /api/announcements/my-feed      → Student feed — course-specific for enrolled courses
 *  GET    /api/announcements/manage       → Admin/Instructor management list
 *  DELETE /api/announcements/{id}         → Soft-delete (Admin or post owner)
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    @Autowired private AnnouncementService announcementService;

    /** Create an announcement (Admin = general or course; Instructor = assigned courses only). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @Valid @RequestBody CreateAnnouncementRequest req,
            @AuthenticationPrincipal User author) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Announcement posted.", announcementService.create(req, author)));
    }

    /** Get all active system-wide (general) announcements — for Student Dashboard. */
    @GetMapping("/system")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> getSystem() {
        return ResponseEntity.ok(ApiResponse.success("System announcements fetched.",
                announcementService.getSystemAnnouncements()));
    }

    /** Get announcements for a specific course. */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> getCourse(
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success("Course announcements fetched.",
                announcementService.getCourseAnnouncements(courseId)));
    }

    /** Student feed — course-specific announcements for all enrolled courses. */
    @GetMapping("/my-feed")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> myFeed(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(ApiResponse.success("Feed fetched.",
                announcementService.getStudentFeed(student)));
    }

    /** Management list — Admins see everything, Instructors see only their own. */
    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> manage(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("Announcements fetched.",
                announcementService.getManageList(user)));
    }

    /** Soft-delete an announcement. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        announcementService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted."));
    }
}
