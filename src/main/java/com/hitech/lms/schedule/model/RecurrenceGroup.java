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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RecurrenceGroup Entity — maps to 'recurrence_groups' table.
 *
 * FR-4.1: Groups all sessions that were created together as a recurring series.
 * Each individual session points back to this group via recurrenceGroupId.
 *
 * Supported frequencies:
 *   DAILY    — every day until repeat_until
 *   WEEKLY   — on specific days of week (daysOfWeek CSV) until repeat_until
 *   BIWEEKLY — every two weeks on specified day(s) until repeat_until
 *   MONTHLY  — same day of month each month until repeat_until
 */
@Entity
@Table(name = "recurrence_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;

    // Comma-separated day abbreviations: MON,TUE,WED,THU,FRI,SAT,SUN
    // Used for WEEKLY and BIWEEKLY only; null for DAILY / MONTHLY
    @Column(name = "days_of_week", length = 50)
    private String daysOfWeek;

    @Column(name = "repeat_until", nullable = false)
    private LocalDate repeatUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ---- Enum ----
    public enum Frequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY
    }
}
