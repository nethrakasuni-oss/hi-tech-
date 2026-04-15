package com.hitech.lms.announcement.service;
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
import com.hitech.lms.announcement.model.Announcement;
import com.hitech.lms.announcement.repository.AnnouncementRepository;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.course.model.Course;
import com.hitech.lms.course.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnnouncementService {

    private static final Logger logger = LoggerFactory.getLogger(AnnouncementService.class);

    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private CourseRepository courseRepository;

    // ---- CREATE announcement (Admin or Instructor) ----
    public AnnouncementResponse create(CreateAnnouncementRequest req, User author) {
        Course course = null;

        if (req.getCourseId() != null) {
            // COURSE announcement — resolve the course and check authorization
            course = courseRepository.findById(req.getCourseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));

            if (author.getRole() == User.Role.INSTRUCTOR) {
                // Instructors can only post to their assigned courses
                boolean isAssigned = course.getInstructors().stream()
                        .anyMatch(i -> i.getId().equals(author.getId()));
                if (!isAssigned) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You can only post announcements for courses you are assigned to.");
                }
            }
            // Admins can post to any course without restriction
        } else {
            // SYSTEM (general) announcement — Admin only
            if (author.getRole() != User.Role.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only Admins can post system-wide announcements. " +
                        "Instructors must select a specific course.");
            }
        }

        Announcement announcement = Announcement.builder()
                .title(req.getTitle().trim())
                .message(req.getMessage().trim())
                .type(course != null ? Announcement.AnnouncementType.COURSE : Announcement.AnnouncementType.SYSTEM)
                .course(course)
                .author(author)
                .isActive(true)
                .build();

        announcement = announcementRepository.save(announcement);
        logger.info("Announcement '{}' created by {} ({})", announcement.getTitle(),
                author.getEmail(), announcement.getType());
        return mapToResponse(announcement);
    }

    // ---- GET system-wide announcements (for all students) ----
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getSystemAnnouncements() {
        return announcementRepository.findActiveSystemAnnouncements()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---- GET course-specific announcements ----
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getCourseAnnouncements(Long courseId) {
        return announcementRepository.findActiveByCourseId(courseId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---- GET full feed for a student (course-specific announcements for enrolled courses) ----
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getStudentFeed(User student) {
        // Get the IDs of all active enrolled courses using existing repository method
        List<Long> enrolledCourseIds = courseRepository
                .findEnrolledCoursesByStudent(student.getId())
                .stream()
                .map(Course::getId)
                .collect(Collectors.toList());

        if (enrolledCourseIds.isEmpty()) return List.of();

        return announcementRepository.findActiveByCourseIds(enrolledCourseIds)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---- GET announcements for management (Admin sees all; Instructor sees own) ----
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getManageList(User user) {
        List<Announcement> list = (user.getRole() == User.Role.ADMIN)
                ? announcementRepository.findAllActive()
                : announcementRepository.findActiveByAuthorId(user.getId());

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ---- DELETE (soft-delete) ----
    public void delete(Long id, User user) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Announcement not found."));

        // Admin can delete any; Instructor can only delete their own
        if (user.getRole() != User.Role.ADMIN
                && !announcement.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only delete your own announcements.");
        }

        announcement.setActive(false);
        announcementRepository.save(announcement);
        logger.info("Announcement {} soft-deleted by {}", id, user.getEmail());
    }

    // ---- Mapper ----
    private AnnouncementResponse mapToResponse(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .message(a.getMessage())
                .type(a.getType().name())
                .courseId(a.getCourse() != null ? a.getCourse().getId() : null)
                .courseTitle(a.getCourse() != null ? a.getCourse().getTitle() : null)
                .courseCode(a.getCourse() != null ? a.getCourse().getCourseCode() : null)
                .authorId(a.getAuthor().getId())
                .authorName(a.getAuthor().getFullName())
                .authorRole(a.getAuthor().getRole().name())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
