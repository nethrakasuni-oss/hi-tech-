package com.hitech.lms.support.controller;
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
import com.hitech.lms.support.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired private AppointmentService appointmentService;

    @GetMapping("/instructors")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getInstructors() {
        return ResponseEntity.ok(ApiResponse.success("Instructors fetched.",
                appointmentService.getInstructorsForBooking()));
    }

    @GetMapping("/availability/{instructorId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AvailabilitySlotResponse>>> getSlots(
            @PathVariable Long instructorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Slots fetched.",
                appointmentService.getAvailableSlots(instructorId, from, to)));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody CreateAppointmentRequest req,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment booked.", appointmentService.bookAppointment(req, student)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(ApiResponse.success("Appointments fetched.",
                appointmentService.listAppointments(viewer, page, size)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirm(
            @PathVariable Long id, @AuthenticationPrincipal User instructor) {
        return ResponseEntity.ok(ApiResponse.success("Confirmed.",
                appointmentService.confirmAppointment(id, instructor)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal User canceller) {
        return ResponseEntity.ok(ApiResponse.success("Cancelled.",
                appointmentService.cancelAppointment(id, reason, canceller)));
    }

    @PostMapping("/availability")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> saveAvailability(
            @RequestBody SaveAvailabilityRequest req,
            @AuthenticationPrincipal User instructor) {
        appointmentService.saveAvailability(req, instructor);
        return ResponseEntity.ok(ApiResponse.success("Availability saved."));
    }
}