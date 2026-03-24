package com.hitech.lms.auth.model;
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

import java.time.LocalDateTime;

/**
 * AuditLog Entity
 * FR-1.4: Every deactivation and reactivation must be logged with admin ID.
 * Also logs profile changes, password resets, etc.
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // What action was performed (e.g. ACCOUNT_DEACTIVATED, PROFILE_UPDATED)
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    // The admin or system that performed the action (nullable = system action)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    // The user the action was performed on
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    // Extra context (e.g. what fields changed)
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ---- Common action constants ----
    public static final String ACTION_ACCOUNT_DEACTIVATED  = "ACCOUNT_DEACTIVATED";
    public static final String ACTION_ACCOUNT_REACTIVATED  = "ACCOUNT_REACTIVATED";
    public static final String ACTION_PROFILE_UPDATED      = "PROFILE_UPDATED";
    public static final String ACTION_PASSWORD_CHANGED     = "PASSWORD_CHANGED";
    public static final String ACTION_PASSWORD_RESET       = "PASSWORD_RESET";
    public static final String ACTION_EMAIL_VERIFIED       = "EMAIL_VERIFIED";
    public static final String ACTION_USER_CREATED         = "USER_CREATED";
    public static final String ACTION_ROLE_CHANGED         = "ROLE_CHANGED";
}