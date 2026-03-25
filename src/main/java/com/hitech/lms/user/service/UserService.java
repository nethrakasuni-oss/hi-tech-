package com.hitech.lms.user.service;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UserService — Handles profile management and admin user operations.
 *
 * FR-1.3: Profile Management (view, update, change password, photo upload)
 * FR-1.4: Account Deactivation / Reactivation (Admin only)
 * FR-1.2: Admin creates Instructor/Admin accounts
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired private UserRepository       userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AuditLogRepository   auditLogRepository;
    @Autowired private PasswordEncoder      passwordEncoder;
    @Autowired private EmailService         emailService;

    // =====================================================
    // FR-1.3: GET OWN PROFILE
    // =====================================================

    /**
     * Returns profile data for a user.
     * Any authenticated user can get their own profile.
     * Admins can get any user's profile.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUserById(userId);
        return mapToProfileResponse(user);
    }

    // =====================================================
    // FR-1.3: UPDATE OWN PROFILE
    // =====================================================

    /**
     * Updates a user's profile information.
     * Email is NEVER changed here — it is read-only (FR-1.3).
     * All changes are logged in the audit trail.
     */
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        // Validate phone number format if provided
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (!request.getPhoneNumber().matches("^(07\\d{8}|7\\d{8})$")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phone number must start with 07 and be 10 digits, or start with 7 and be 9 digits"
                );
            }
            user.setPhoneNumber(request.getPhoneNumber());
        } else {
            user.setPhoneNumber(null);
        }

        user.setFullName(request.getFullName().trim());
        user.setBio(request.getBio());
        if (request.getLanguagePreference() != null) {
            user.setLanguagePreference(request.getLanguagePreference());
        }

        userRepository.save(user);

        // Audit log — record what was changed
        saveAuditLog(AuditLog.ACTION_PROFILE_UPDATED, user, user,
                "Profile updated: name=" + user.getFullName());

        logger.info("Profile updated for user: {}", user.getEmail());
        return mapToProfileResponse(user);
    }

    // =====================================================
    // FR-1.3: CHANGE PASSWORD (from profile)
    // =====================================================

    /**
     * Changes a user's own password.
     * Requires the current password to be entered first (FR-1.3).
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {

        // Validate new passwords match
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Passwords do not match."
            );
        }

        User user = findUserById(userId);

        // Verify current password — reject if wrong (FR-1.3)
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Incorrect current password."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Audit log
        saveAuditLog(AuditLog.ACTION_PASSWORD_CHANGED, user, user,
                "Password changed by user");

        logger.info("Password changed for user: {}", user.getEmail());
    }

    // =====================================================
    // ADMIN: CREATE INSTRUCTOR OR ADMIN ACCOUNT
    // FR-1.1: Instructors and Admins are created by Admin
    // =====================================================

    /**
     * Admin creates a new Instructor or Admin account.
     * Students cannot be created this way — they self-register.
     */
    public UserProfileResponse adminCreateUser(AdminCreateUserRequest request, User adminUser) {

        // Validate that Admin is not trying to create a Student via admin panel
        if (request.getRole() == User.Role.STUDENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Students must self-register. Use this form for Instructor or Admin accounts only."
            );
        }

        // Check for duplicate email → 409 (FR-1.1)
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This email is already registered."
            );
        }

        // Validate phone if provided
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (!request.getPhoneNumber().matches("^(07\\d{8}|7\\d{8})$")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phone number must start with 07 and be 10 digits, or start with 7 and be 9 digits"
                );
            }
        }

        // Determine password — use provided temp password or generate one
        String passwordToSet = (request.getTemporaryPassword() != null
                && !request.getTemporaryPassword().isBlank())
                ? request.getTemporaryPassword()
                : UUID.randomUUID().toString().substring(0, 12) + "Aa1!"; // auto-generated

        // Admin-created accounts are ACTIVE immediately (no email verification needed)
        User newUser = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(passwordToSet))
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole())
                .accountStatus(User.AccountStatus.ACTIVE) // Active immediately
                .build();

        newUser = userRepository.save(newUser);

        // Send welcome email with credentials
        emailService.sendAccountCreatedEmail(
                newUser.getEmail(),
                newUser.getFullName(),
                newUser.getRole().name(),
                passwordToSet
        );

        // Audit log
        saveAuditLog(AuditLog.ACTION_USER_CREATED, adminUser, newUser,
                "User created by admin: role=" + newUser.getRole().name());

        logger.info("Admin {} created user: {} ({})",
                adminUser.getEmail(), newUser.getEmail(), newUser.getRole());

        return mapToProfileResponse(newUser);
    }

    // =====================================================
    // ADMIN: GET PAGINATED USER LIST
    // =====================================================

    /**
     * Returns a paginated, searchable, filterable list of all users.
     * Used by the Admin User Management screen.
     */
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getUsers(
            String search,
            String role,
            String status,
            int page,
            int size,
            String sortBy
    ) {
        // Convert string filters to enum (null = no filter = show all)
        User.Role roleEnum = (role != null && !role.isBlank()) ? User.Role.valueOf(role) : null;
        User.AccountStatus statusEnum = (status != null && !status.isBlank())
                ? User.AccountStatus.valueOf(status) : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return userRepository.searchUsers(search, roleEnum, statusEnum, pageable)
                .map(this::mapToSummaryResponse);
    }

    // =====================================================
    // ADMIN: GET SINGLE USER
    // =====================================================

    @Transactional(readOnly = true)
    public UserProfileResponse adminGetUser(Long userId) {
        return mapToProfileResponse(findUserById(userId));
    }

    // =====================================================
    // ADMIN: UPDATE USER (edit name, role, status)
    // =====================================================

    public UserProfileResponse adminUpdateUser(Long targetUserId, AdminUpdateUserRequest request,
                                               User adminUser) {
        User targetUser = findUserById(targetUserId);

        // Admin cannot change their own role (FR-1.2 — prevent lockout)
        if (targetUserId.equals(adminUser.getId()) && request.getRole() != null
                && request.getRole() != adminUser.getRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot change your own role."
            );
        }

        String changes = "";

        if (request.getFullName() != null) {
            targetUser.setFullName(request.getFullName().trim());
            changes += "name updated; ";
        }

        if (request.getPhoneNumber() != null) {
            targetUser.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getRole() != null && request.getRole() != targetUser.getRole()) {
            changes += "role changed from " + targetUser.getRole() + " to " + request.getRole() + "; ";
            targetUser.setRole(request.getRole());
        }

        userRepository.save(targetUser);

        saveAuditLog(AuditLog.ACTION_PROFILE_UPDATED, adminUser, targetUser,
                "Admin updated user: " + changes);

        return mapToProfileResponse(targetUser);
    }

    // =====================================================
    // ADMIN: BULK IMPORT USERS
    // =====================================================

    /**
     * Bulk imports users from a CSV file.
     */
    public BulkImportSummaryResponse bulkImportUsers(MultipartFile file, User adminUser) {
        List<BulkImportRowResult> results = new ArrayList<>();
        int created = 0, skipped = 0, errors = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine != null && headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1); // strip BOM
            }
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }

            String[] headers = headerLine.split(",");
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }

            Integer nameIdx = headerMap.get("fullname");
            Integer emailIdx = headerMap.get("email");
            Integer roleIdx = headerMap.get("role");
            Integer phoneIdx = headerMap.get("phonenumber");

            if (nameIdx == null || emailIdx == null || roleIdx == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required headers: fullName, email, role");
            }

            String line;
            int rowNumber = 1; // row 1 was header
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) continue;

                String[] columns = line.split(",", -1);
                String email = columns.length > emailIdx ? columns[emailIdx].trim().toLowerCase() : "";
                String name = columns.length > nameIdx ? columns[nameIdx].trim() : "";
                String roleStr = columns.length > roleIdx ? columns[roleIdx].trim().toUpperCase() : "";
                String phone = (phoneIdx != null && columns.length > phoneIdx) ? columns[phoneIdx].trim() : null;

                if (email.isEmpty() || name.isEmpty() || roleStr.isEmpty()) {
                    results.add(new BulkImportRowResult(rowNumber, "ERROR", email, name, "Missing required fields"));
                    errors++;
                    continue;
                }

                if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    results.add(new BulkImportRowResult(rowNumber, "ERROR", email, name, "Invalid email format"));
                    errors++;
                    continue;
                }

                if (userRepository.existsByEmail(email)) {
                    results.add(new BulkImportRowResult(rowNumber, "SKIPPED", email, name, "Email already exists"));
                    skipped++;
                    continue;
                }

                User.Role role;
                try {
                    role = User.Role.valueOf(roleStr);
                } catch (IllegalArgumentException e) {
                    results.add(new BulkImportRowResult(rowNumber, "ERROR", email, name, "Invalid role: " + roleStr));
                    errors++;
                    continue;
                }

                if (phone != null && !phone.isBlank() && !phone.matches("^(07\\d{8}|7\\d{8})$")) {
                    results.add(new BulkImportRowResult(rowNumber, "ERROR", email, name, "Phone number must start with 07 (10 digits) or 7 (9 digits)"));
                    errors++;
                    continue;
                }

                try {
                    String tempPassword = UUID.randomUUID().toString().substring(0, 12) + "Aa1!";
                    User newUser = User.builder()
                            .fullName(name)
                            .email(email)
                            .passwordHash(passwordEncoder.encode(tempPassword))
                            .phoneNumber((phone != null && !phone.isBlank()) ? phone : null)
                            .role(role)
                            .accountStatus(User.AccountStatus.ACTIVE)
                            .build();

                    userRepository.save(newUser);
                    emailService.sendAccountCreatedEmail(email, name, role.name(), tempPassword);
                    saveAuditLog(AuditLog.ACTION_USER_CREATED, adminUser, newUser, "User bulk imported: role=" + role.name());

                    results.add(new BulkImportRowResult(rowNumber, "CREATED", email, name, "Account created successfully"));
                    created++;
                } catch (Exception e) {
                    logger.error("Error creating user row " + rowNumber, e);
                    results.add(new BulkImportRowResult(rowNumber, "ERROR", email, name, "System error during creation"));
                    errors++;
                }
            }

        } catch (Exception e) {
             if (e instanceof ResponseStatusException) throw (ResponseStatusException) e;
             logger.error("Failed to parse CSV", e);
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse CSV file");
        }

        return new BulkImportSummaryResponse(created + skipped + errors, created, skipped, errors, results);
    }

    // =====================================================
    // FR-1.4: ACCOUNT DEACTIVATION
    // =====================================================

    /**
     * Deactivates a user account.
     *
     * FR-1.4 workflow:
     * 1. Set account_status = INACTIVE
     * 2. Delete ALL refresh tokens for that user (forces session termination)
     * 3. Write audit log
     * 4. Optionally send notification email
     *
     * The user will receive 401 on their next API call (JWT filter checks account status).
     */
    public void deactivateUser(Long targetUserId, User adminUser, boolean sendEmail) {

        // Admin cannot deactivate themselves (FR-1.4)
        if (targetUserId.equals(adminUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot deactivate your own account."
            );
        }

        User targetUser = findUserById(targetUserId);

        if (targetUser.getAccountStatus() == User.AccountStatus.INACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This account is already deactivated."
            );
        }

        // Step 1: Set status to INACTIVE
        targetUser.setAccountStatus(User.AccountStatus.INACTIVE);
        userRepository.save(targetUser);

        // Step 2: Purge ALL active refresh tokens → session termination (FR-1.4)
        refreshTokenRepository.deleteAllByUserId(targetUserId);

        // Step 3: Audit log
        saveAuditLog(AuditLog.ACTION_ACCOUNT_DEACTIVATED, adminUser, targetUser,
                "Account deactivated by admin: " + adminUser.getEmail());

        // Step 4: Optional notification email
        if (sendEmail) {
            emailService.sendAccountDeactivationEmail(
                    targetUser.getEmail(), targetUser.getFullName()
            );
        }

        logger.info("Account deactivated: {} by admin: {}",
                targetUser.getEmail(), adminUser.getEmail());
    }

    // =====================================================
    // FR-1.4: ACCOUNT REACTIVATION
    // =====================================================

    /**
     * Reactivates a previously deactivated account.
     * Simply sets status back to ACTIVE.
     */
    public void reactivateUser(Long targetUserId, User adminUser) {
        User targetUser = findUserById(targetUserId);

        if (targetUser.getAccountStatus() == User.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This account is already active."
            );
        }

        targetUser.setAccountStatus(User.AccountStatus.ACTIVE);
        userRepository.save(targetUser);

        saveAuditLog(AuditLog.ACTION_ACCOUNT_REACTIVATED, adminUser, targetUser,
                "Account reactivated by admin: " + adminUser.getEmail());

        logger.info("Account reactivated: {} by admin: {}",
                targetUser.getEmail(), adminUser.getEmail());
    }

    // =====================================================
    // ADMIN DASHBOARD: User count KPIs
    // =====================================================

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats() {
        return UserStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalAdmins(userRepository.countByRole(User.Role.ADMIN))
                .totalInstructors(userRepository.countByRole(User.Role.INSTRUCTOR))
                .totalStudents(userRepository.countByRole(User.Role.STUDENT))
                .activeUsers(userRepository.countByAccountStatus(User.AccountStatus.ACTIVE))
                .inactiveUsers(userRepository.countByAccountStatus(User.AccountStatus.INACTIVE))
                .pendingVerification(
                        userRepository.countByAccountStatus(User.AccountStatus.PENDING_VERIFICATION)
                )
                .build();
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."
                ));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())              // read-only in profile screen
                .phoneNumber(user.getPhoneNumber())
                .bio(user.getBio())
                .languagePreference(user.getLanguagePreference())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .role(user.getRole().name())
                .accountStatus(user.getAccountStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserSummaryResponse mapToSummaryResponse(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .accountStatus(user.getAccountStatus().name())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void saveAuditLog(String action, User performedBy, User targetUser, String details) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .performedBy(performedBy)
                .targetUser(targetUser)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}