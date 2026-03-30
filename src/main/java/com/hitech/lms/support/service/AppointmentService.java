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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppointmentService {

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private InstructorAvailabilityRepository availabilityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailService emailService;

    private static final int MAX_ACTIVE_BOOKINGS = 2;
    private static final int MIN_ADVANCE_HOURS = 24;

    // ---- SAVE INSTRUCTOR AVAILABILITY ----
    public void saveAvailability(SaveAvailabilityRequest req, User instructor) {
        // Clear existing weekly slots (not blocked dates — manage separately)
        List<InstructorAvailability> existing = availabilityRepository.findByInstructorId(instructor.getId());
        existing.stream().filter(a -> a.getDayOfWeek() != null)
                .forEach(availabilityRepository::delete);

        if (req.getWeeklySlots() != null) {
            for (SaveAvailabilityRequest.DaySlot slot : req.getWeeklySlots()) {
                if (slot.isAvailable() && slot.getStartTime() != null && slot.getEndTime() != null) {
                    availabilityRepository.save(InstructorAvailability.builder()
                            .instructor(instructor)
                            .dayOfWeek(slot.getDayOfWeek())
                            .startTime(slot.getStartTime())
                            .endTime(slot.getEndTime())
                            .sessionDurationMinutes(req.getSessionDurationMinutes())
                            .bufferMinutes(req.getBufferMinutes())
                            .blocked(false)
                            .build());
                }
            }
        }
        if (req.getBlockedDates() != null) {
            for (LocalDate d : req.getBlockedDates()) {
                availabilityRepository.save(InstructorAvailability.builder()
                        .instructor(instructor).blockedDate(d).blocked(true).build());
            }
        }
    }

    // ---- GET AVAILABLE SLOTS FOR AN INSTRUCTOR (for a given date range) ----
    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getAvailableSlots(Long instructorId,
                                                            LocalDate from, LocalDate to) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found."));

        List<InstructorAvailability> weekly =
                availabilityRepository.findByInstructorIdAndBlockedFalseOrderByDayOfWeekAsc(instructorId);
        List<InstructorAvailability> blocked =
                availabilityRepository.findByInstructorIdAndBlockedDateNotNull(instructorId);
        Set<LocalDate> blockedDates = blocked.stream()
                .map(InstructorAvailability::getBlockedDate).collect(Collectors.toSet());

        List<AvailabilitySlotResponse> slots = new ArrayList<>();
        LocalDate cursor = from;

        while (!cursor.isAfter(to)) {
            if (blockedDates.contains(cursor)) { cursor = cursor.plusDays(1); continue; }
            final LocalDate day = cursor;
            Optional<InstructorAvailability> dayAvail = weekly.stream()
                    .filter(a -> a.getDayOfWeek() == day.getDayOfWeek()).findFirst();

            if (dayAvail.isPresent()) {
                InstructorAvailability avail = dayAvail.get();
                int duration = avail.getSessionDurationMinutes();
                int buffer   = avail.getBufferMinutes();
                LocalTime current = avail.getStartTime();

                while (!current.plusMinutes(duration).isAfter(avail.getEndTime())) {
                    LocalTime slotEnd = current.plusMinutes(duration);
                    final LocalTime slotStart = current;
                    boolean booked = !appointmentRepository.findConflicts(
                            instructorId, day, slotStart, slotEnd, null).isEmpty();

                    slots.add(AvailabilitySlotResponse.builder()
                            .date(day).startTime(slotStart).endTime(slotEnd)
                            .available(!booked).build());
                    current = slotEnd.plusMinutes(buffer);
                }
            }
            cursor = cursor.plusDays(1);
        }
        return slots;
    }

    // ---- BOOK APPOINTMENT (Student) ----
    public AppointmentResponse bookAppointment(CreateAppointmentRequest req, User student) {
        // Advance booking check
        if (req.getAppointmentDate().isBefore(LocalDate.now().plusDays(1)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Appointments must be booked at least 24 hours in advance.");

        // Active bookings cap
        long active = appointmentRepository.countActiveByStudent(student.getId());
        if (active >= MAX_ACTIVE_BOOKINGS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You already have " + MAX_ACTIVE_BOOKINGS + " active appointments. Please wait or cancel one.");

        User instructor = userRepository.findById(req.getInstructorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found."));
        if (instructor.getRole() != User.Role.INSTRUCTOR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected user is not an instructor.");

        // Compute end time from availability settings
        List<InstructorAvailability> weekly =
                availabilityRepository.findByInstructorIdAndBlockedFalseOrderByDayOfWeekAsc(req.getInstructorId());
        int duration = weekly.isEmpty() ? 30 : weekly.get(0).getSessionDurationMinutes();
        LocalTime endTime = req.getStartTime().plusMinutes(duration);

        // Conflict check
        if (!appointmentRepository.findConflicts(req.getInstructorId(),
                req.getAppointmentDate(), req.getStartTime(), endTime, null).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This slot is no longer available. Please choose another time.");

        Appointment apt = Appointment.builder()
                .student(student).instructor(instructor)
                .appointmentDate(req.getAppointmentDate())
                .startTime(req.getStartTime()).endTime(endTime)
                .reason(req.getReason()).notes(req.getNotes())
                .status(Appointment.AppointmentStatus.PENDING)
                .build();

        apt = appointmentRepository.save(apt);
        notifyInstructor(apt, "New Appointment Request from " + student.getFullName());
        return mapToResponse(apt);
    }

    // ---- CONFIRM APPOINTMENT (Instructor) ----
    public AppointmentResponse confirmAppointment(Long aptId, User instructor) {
        Appointment apt = findById(aptId);
        if (!apt.getInstructor().getId().equals(instructor.getId())
                && instructor.getRole() != User.Role.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        apt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        apt = appointmentRepository.save(apt);
        notifyStudent(apt, "Your appointment has been confirmed");
        return mapToResponse(apt);
    }

    // ---- CANCEL APPOINTMENT ----
    public AppointmentResponse cancelAppointment(Long aptId, String reason, User canceller) {
        Appointment apt = findById(aptId);
        boolean isInstructor = apt.getInstructor().getId().equals(canceller.getId());
        boolean isStudent    = apt.getStudent().getId().equals(canceller.getId());
        if (!isInstructor && !isStudent && canceller.getRole() != User.Role.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");

        apt.setStatus(Appointment.AppointmentStatus.CANCELLED);
        apt.setCancellationReason(reason);
        apt = appointmentRepository.save(apt);

        if (isInstructor) notifyStudent(apt, "Your appointment has been cancelled by the instructor");
        else notifyInstructor(apt, "Appointment cancelled by student");
        return mapToResponse(apt);
    }

    // ---- LIST (Student or Instructor) ----
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> listAppointments(User viewer, int page, int size) {
        Pageable pg = PageRequest.of(page, size);
        if (viewer.getRole() == User.Role.STUDENT) {
            return appointmentRepository.findByStudentIdOrderByAppointmentDateDesc(viewer.getId(), pg)
                    .map(this::mapToResponse);
        }
        return appointmentRepository.findByInstructorIdOrderByAppointmentDateAsc(viewer.getId(), pg)
                .map(this::mapToResponse);
    }

    // ---- GET ALL INSTRUCTORS (for booking selector) ----
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getInstructorsForBooking() {
        return userRepository.searchUsers(null, User.Role.INSTRUCTOR,
                        User.AccountStatus.ACTIVE, PageRequest.of(0, 100))
                .stream()
                .map(u -> UserSummaryResponse.builder()
                        .id(u.getId()).fullName(u.getFullName()).email(u.getEmail())
                        .role(u.getRole().name()).accountStatus(u.getAccountStatus().name())
                        .profilePhotoUrl(u.getProfilePhotoUrl()).createdAt(u.getCreatedAt()).build())
                .collect(Collectors.toList());
    }

    private Appointment findById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found."));
    }

    private void notifyStudent(Appointment apt, String subject) {
        emailService.sendScheduleNotificationEmail(apt.getStudent().getEmail(), subject,
                "Dear " + apt.getStudent().getFullName() + ",",
                buildAptInfo(apt));
    }
    private void notifyInstructor(Appointment apt, String subject) {
        emailService.sendScheduleNotificationEmail(apt.getInstructor().getEmail(), subject,
                "Dear " + apt.getInstructor().getFullName() + ",",
                buildAptInfo(apt));
    }
    private String buildAptInfo(Appointment apt) {
        return "<strong>Date:</strong> " + apt.getAppointmentDate() +
                "<br><strong>Time:</strong> " + apt.getStartTime() + " – " + apt.getEndTime() +
                "<br><strong>Reason:</strong> " + apt.getReason().name().replace("_"," ");
    }

    private AppointmentResponse mapToResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .studentId(a.getStudent().getId()).studentName(a.getStudent().getFullName())
                .studentEmail(a.getStudent().getEmail())
                .instructorId(a.getInstructor().getId()).instructorName(a.getInstructor().getFullName())
                .instructorEmail(a.getInstructor().getEmail())
                .appointmentDate(a.getAppointmentDate())
                .startTime(a.getStartTime()).endTime(a.getEndTime())
                .reason(a.getReason().name()).notes(a.getNotes())
                .cancellationReason(a.getCancellationReason())
                .status(a.getStatus().name()).createdAt(a.getCreatedAt()).build();
    }
}