package com.hitech.lms.course.model;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Course Entity — maps to the 'courses' table.
 *
 * FR-2.1: Course lifecycle: DRAFT → ACTIVE → ARCHIVED
 * - DRAFT:    Created, not visible to students
 * - ACTIVE:   Published, visible and enrollable by students
 * - ARCHIVED: Closed, removed from catalog but data preserved
 *
 * A Course belongs to one Program.
 * A Course can have many Instructors (many-to-many via course_instructors).
 * A Course can have many ContentNodes (Week/Module/Topic hierarchy).
 * A Course can have many Enrollments.
 */
@Entity
@Table(name = "courses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Basic Info ----
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "course_code", nullable = false, unique = true, length = 20)
    private String courseCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ---- Program / Category ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    // ---- Duration ----
    @Column(name = "duration_value")
    private Integer durationValue;   // e.g. 12

    @Column(name = "duration_unit", length = 20)
    private String durationUnit;    // DAYS, WEEKS, MONTHS

    // ---- Financial ----
    // FR-2.1: Course fee is set at creation and cannot be changed after enrollments exist
    @Column(name = "course_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal courseFee = BigDecimal.ZERO;

    // ---- Enrollment Settings ----
    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_type", nullable = false)
    @Builder.Default
    private EnrollmentType enrollmentType = EnrollmentType.OPEN;

    @Column(name = "max_students")
    private Integer maxStudents;    // NULL = no cap

    // ---- Media ----
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // ---- Progress Tracking ----
    @Column(name = "current_progress", length = 100)
    private String currentProgress;

    // ---- Lifecycle Status ----
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    // ---- Instructors (many-to-many) ----
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_instructors",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "instructor_id")
    )
    @Builder.Default
    private List<User> instructors = new ArrayList<>();

    // ---- Audit ----
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

    /**
     * FR-2.1 state machine.
     * DRAFT    → can be edited freely, not visible to students
     * ACTIVE   → published, visible in catalog, students can enroll
     * ARCHIVED → read-only, removed from catalog, data preserved
     */
    public enum CourseStatus {
        DRAFT,
        ACTIVE,
        ARCHIVED
    }

    /**
     * OPEN      → students can discover and enroll (after paying if there's a fee)
     * ADMIN_ONLY → enrollment only via Admin management panel
     */
    public enum EnrollmentType {
        OPEN,
        ADMIN_ONLY
    }
}