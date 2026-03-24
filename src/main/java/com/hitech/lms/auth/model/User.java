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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User Entity — maps to the 'users' database table.
 *
 * This is the central model for the entire LMS. Every user —
 * whether Admin, Instructor, or Student — is stored here.
 *
 * Roles are FIXED (ADMIN, INSTRUCTOR, STUDENT) — they cannot be
 * created or deleted via the UI. This matches FR-1.2 RBAC design.
 */
@Entity
@Table(name = "users")
@Data                   // Lombok: generates getters, setters, equals, hashCode, toString
@Builder                // Lombok: enables builder pattern (User.builder().email(...).build())
@NoArgsConstructor      // Lombok: generates no-arg constructor (required by JPA)
@AllArgsConstructor     // Lombok: generates all-args constructor (used by @Builder)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Full name — required, max 100 chars (matches FR-1.3 validation)
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    // Email — must be unique across the entire system
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // Password stored as bcrypt hash — NEVER the raw password
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Optional phone number (format: 07XXXXXXXX)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // Optional bio — max 500 characters (matches FR-1.3)
    @Column(name = "bio", length = 500)
    private String bio;

    // UI language preference — defaults to English
    @Column(name = "language_preference", nullable = false, length = 10)
    @Builder.Default
    private String languagePreference = "en";

    // URL to user's profile photo (stored in server or object storage)
    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    // Role — one of three fixed values defined in Role enum
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // Account lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    // Automatically set when the record is created
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Automatically updated every time the record is saved
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---- Inner Enums ----

    /**
     * Three fixed roles — hardcoded as per FRS design decision.
     * Permissions for each role are defined in code, NOT in the database.
     */
    public enum Role {
        ADMIN,
        INSTRUCTOR,
        STUDENT,
        SUPPORT_STAFF
    }

    /**
     * Account lifecycle states:
     * PENDING_VERIFICATION — registered but email not yet confirmed
     * ACTIVE              — fully operational account
     * INACTIVE            — suspended by Admin (FR-1.4)
     */
    public enum AccountStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        INACTIVE
    }
}