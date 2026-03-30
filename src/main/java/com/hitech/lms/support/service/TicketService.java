package com.hitech.lms.support.service;
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


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.support.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Transactional
public class TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    @Autowired private SupportTicketRepository ticketRepository;
    @Autowired private TicketMessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailService emailService;

    // ---- CREATE TICKET (Student) ----
    public TicketResponse createTicket(CreateTicketRequest req, User student) {
        int year = LocalDate.now().getYear();
        long count = ticketRepository.countByYear(year);
        String ticketNumber = String.format("TKT-%d-%05d", year, count + 1);

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNumber)
                .student(student)
                .category(req.getCategory())
                .subject(req.getSubject().trim())
                .description(req.getDescription().trim())
                .priority(req.getPriority() != null ? req.getPriority() : SupportTicket.TicketPriority.MEDIUM)
                .status(SupportTicket.TicketStatus.OPEN)
                .build();

        ticket = ticketRepository.save(ticket);
        logger.info("Ticket {} created by {}", ticketNumber, student.getEmail());
        // send confirmation email (fire-and-forget)
        notifyStudent(ticket, "Your support ticket has been received.",
                "Ticket ID: " + ticketNumber + " | Subject: " + ticket.getSubject());
        return mapToResponse(ticket, false);
    }

    // ---- GET TICKET BY ID ----
    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long ticketId, User viewer) {
        SupportTicket ticket = findTicketById(ticketId);
        boolean isStaff = viewer.getRole() == User.Role.SUPPORT_STAFF
                || viewer.getRole() == User.Role.ADMIN;
        if (!isStaff && !ticket.getStudent().getId().equals(viewer.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        return mapToResponse(ticket, isStaff);
    }

    // ---- LIST TICKETS (Student: own; Staff/Admin: all) ----
    @Transactional(readOnly = true)
    public Page<TicketResponse> listTickets(User viewer, String status, String category,
                                            String search, int page, int size) {
        Pageable pg = PageRequest.of(page, size);
        SupportTicket.TicketStatus st = (status != null && !status.isBlank())
                ? SupportTicket.TicketStatus.valueOf(status) : null;
        SupportTicket.TicketCategory cat = (category != null && !category.isBlank())
                ? SupportTicket.TicketCategory.valueOf(category) : null;

        if (viewer.getRole() == User.Role.STUDENT) {
            return ticketRepository.findByStudentWithFilter(viewer.getId(), st, pg)
                    .map(t -> mapToResponse(t, false));
        }
        return ticketRepository.findAllWithFilters(st, cat, search, pg)
                .map(t -> mapToResponse(t, true));
    }

    // ---- UPDATE TICKET (Staff/Admin) ----
    public TicketResponse updateTicket(Long ticketId, UpdateTicketRequest req, User staff) {
        validateStaff(staff);
        SupportTicket ticket = findTicketById(ticketId);

        if (req.getStatus() != null) {
            SupportTicket.TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(req.getStatus());
            if (req.getStatus() == SupportTicket.TicketStatus.RESOLVED) {
                if (req.getResolutionNote() == null || req.getResolutionNote().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Resolution note is required when resolving a ticket.");
                ticket.setResolutionNote(req.getResolutionNote());
                ticket.setResolvedAt(LocalDateTime.now());
                notifyStudent(ticket, "Your support ticket has been resolved.",
                        "Resolution: " + req.getResolutionNote());
            }
        }
        if (req.getPriority() != null) ticket.setPriority(req.getPriority());
        if (req.getAssignedToId() != null) {
            User agent = userRepository.findById(req.getAssignedToId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found."));
            ticket.setAssignedTo(agent);
        }
        if (req.getResolutionNote() != null) ticket.setResolutionNote(req.getResolutionNote());

        ticket = ticketRepository.save(ticket);
        return mapToResponse(ticket, true);
    }

    // ---- REOPEN TICKET (Student, within 7 days) ----
    public TicketResponse reopenTicket(Long ticketId, User student) {
        SupportTicket ticket = findTicketById(ticketId);
        if (!ticket.getStudent().getId().equals(student.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        if (ticket.getStatus() != SupportTicket.TicketStatus.RESOLVED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only RESOLVED tickets can be reopened.");
        if (ticket.getResolvedAt() != null
                && LocalDateTime.now().isAfter(ticket.getResolvedAt().plusDays(7)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The 7-day reopen window has passed. Please submit a new ticket.");

        ticket.setStatus(SupportTicket.TicketStatus.OPEN);
        ticket.setResolvedAt(null);
        ticket = ticketRepository.save(ticket);
        return mapToResponse(ticket, false);
    }

    // ---- ADD MESSAGE ----
    public TicketResponse addMessage(Long ticketId, AddMessageRequest req, User sender) {
        SupportTicket ticket = findTicketById(ticketId);
        boolean isStaff = sender.getRole() == User.Role.SUPPORT_STAFF
                || sender.getRole() == User.Role.ADMIN;
        if (!isStaff && !ticket.getStudent().getId().equals(sender.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");

        // Students cannot add internal notes
        boolean internal = isStaff && req.isInternal();

        TicketMessage msg = TicketMessage.builder()
                .ticket(ticket).sender(sender)
                .body(req.getBody()).internal(internal)
                .build();
        messageRepository.save(msg);

        // Notify the other party
        if (isStaff) {
            notifyStudent(ticket, "Update on your support ticket",
                    "The support team has replied to ticket " + ticket.getTicketNumber());
        }
        return mapToResponse(ticket, isStaff);
    }

    // ---- STATS ----
    @Transactional(readOnly = true)
    public TicketStatsResponse getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = todayStart.plusDays(1);
        return TicketStatsResponse.builder()
                .openCount(ticketRepository.countByStatus(SupportTicket.TicketStatus.OPEN))
                .inProgressCount(ticketRepository.countByStatus(SupportTicket.TicketStatus.IN_PROGRESS))
                .resolvedTodayCount(ticketRepository.countResolvedBetween(todayStart, todayEnd))
                .totalCount(ticketRepository.count())
                .build();
    }

    // ---- PRIVATE HELPERS ----
    private SupportTicket findTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
    }

    private void validateStaff(User user) {
        if (user.getRole() != User.Role.SUPPORT_STAFF && user.getRole() != User.Role.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions.");
    }

    private void notifyStudent(SupportTicket ticket, String subject, String detail) {
        try {
            emailService.sendScheduleNotificationEmail(
                    ticket.getStudent().getEmail(), subject,
                    "Dear " + ticket.getStudent().getFullName() + ",",
                    detail + "<br>Ticket ID: <strong>" + ticket.getTicketNumber() + "</strong>");
        } catch (Exception e) { /* non-critical */ }
    }

    private TicketResponse mapToResponse(SupportTicket t, boolean includeInternal) {
        var msgs = messageRepository.findByTicketIdOrderByCreatedAtAsc(t.getId())
                .stream()
                .filter(m -> includeInternal || !m.isInternal())
                .map(m -> TicketResponse.MessageResponse.builder()
                        .id(m.getId())
                        .senderId(m.getSender().getId())
                        .senderName(m.getSender().getFullName())
                        .senderRole(m.getSender().getRole().name())
                        .body(m.getBody())
                        .internal(m.isInternal())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return TicketResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .studentId(t.getStudent().getId())
                .studentName(t.getStudent().getFullName())
                .studentEmail(t.getStudent().getEmail())
                .assignedToId(t.getAssignedTo() != null ? t.getAssignedTo().getId() : null)
                .assignedToName(t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null)
                .category(t.getCategory().name())
                .subject(t.getSubject())
                .description(t.getDescription())
                .priority(t.getPriority().name())
                .status(t.getStatus().name())
                .resolutionNote(t.getResolutionNote())
                .resolvedAt(t.getResolvedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .messages(msgs)
                .build();
    }
}