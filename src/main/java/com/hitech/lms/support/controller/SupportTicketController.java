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
import com.hitech.lms.support.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    @Autowired private TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<TicketResponse>> create(
            @Valid @RequestBody CreateTicketRequest req,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ticket created.", ticketService.createTicket(req, student)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(ApiResponse.success("Tickets fetched.",
                ticketService.listTickets(viewer, status, category, search, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TicketResponse>> get(
            @PathVariable Long id, @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(ApiResponse.success("Ticket fetched.",
                ticketService.getTicket(id, viewer)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> update(
            @PathVariable Long id, @RequestBody UpdateTicketRequest req,
            @AuthenticationPrincipal User staff) {
        return ResponseEntity.ok(ApiResponse.success("Ticket updated.",
                ticketService.updateTicket(id, req, staff)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<TicketResponse>> reopen(
            @PathVariable Long id, @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(ApiResponse.success("Ticket reopened.",
                ticketService.reopenTicket(id, student)));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TicketResponse>> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody AddMessageRequest req,
            @AuthenticationPrincipal User sender) {
        return ResponseEntity.ok(ApiResponse.success("Message sent.",
                ticketService.addMessage(id, req, sender)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<TicketStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success("Stats fetched.", ticketService.getStats()));
    }
}