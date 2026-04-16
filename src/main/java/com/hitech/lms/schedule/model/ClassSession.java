package com.hitech.lms.schedule.model;
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


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ClassSession Entity — maps to 'class_sessions' table.
 *
 * FR-4.1: A class_session represents one scheduled class (online or physical).
 *
 * Status lifecycle:
 *   SCHEDULED  — upcoming or in-progress; visible to all relevant users
 *   CANCELLED  — admin cancelled; shown with strikethrough in calendar
 *   COMPLETED  — session is in the past (can be auto-set by a scheduler)
 *
 * Recurrence:
 *   For recurring sessions, all sessions in the series share a RecurrenceGroup.
 *   This allows "edit this only / this and future / all" patterns.
 */
@Entity
@Table(name = "class_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Core associations ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    // Null for non-recurring sessions
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_group_id")
    private RecurrenceGroup recurrenceGroup;

    // ---- Session details ----

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Whether this session is conducted online or physically in-person.
     * Drives which fields (platform/link vs location) are populated.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false)
    @Builder.Default
    private ClassType classType = ClassType.ONLINE;

    // ---- Online-specific fields (populated when classType = ONLINE) ----

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform")   // nullable — not required for physical sessions
    private MeetingPlatform meetingPlatform;

    // Must start with https:// (validated in service when classType = ONLINE)
    @Column(name = "meeting_link", length = 500)  // nullable — not required for physical sessions
    private String meetingLink;

    // ---- Physical-specific fields (populated when classType = PHYSICAL) ----

    // e.g. "Room 204, Main Building" or "City Hall, Colombo"
    @Column(name = "class_location", length = 300)  // nullable — not required for online sessions
    private String classLocation;

    // Optional notes visible to enrolled students
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ---- Lifecycle ----

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---- Enums ----

    public enum SessionStatus {
        SCHEDULED,
        CANCELLED,
        COMPLETED
    }

    public enum ClassType {
        ONLINE,
        PHYSICAL
    }

    public enum MeetingPlatform {
        ZOOM,
        TEAMS,
        GOOGLE_MEET,
        CUSTOM
    }
}