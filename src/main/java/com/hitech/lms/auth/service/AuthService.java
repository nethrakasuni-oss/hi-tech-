package com.hitech.lms.auth.service;
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
import com.hitech.lms.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuthService — All authentication business logic.
 *
 * Covers FR-1.1 (Registration + Email Verification),
 *          FR-1.2 (Login + JWT issuance),
 *          and Forgot/Reset Password flows.
 *
 * @Transactional ensures that if any step fails, ALL database changes
 * in that method are rolled back. This prevents partial data saves.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired private UserRepository              userRepository;
    @Autowired private EmailVerificationTokenRepository emailTokenRepository;
    @Autowired private PasswordResetTokenRepository    passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository          refreshTokenRepository;
    @Autowired private AuditLogRepository              auditLogRepository;
    @Autowired private PasswordEncoder                 passwordEncoder;
    @Autowired private JwtUtils                        jwtUtils;
    @Autowired private EmailService                    emailService;

    @Value("${app.email-verification.expiry-minutes}")
    private int emailVerificationExpiryMinutes;

    @Value("${app.password-reset.expiry-minutes}")
    private int passwordResetExpiryMinutes;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshTokenExpiryMs;

    // =====================================================
    // FR-1.1: STUDENT SELF-REGISTRATION
    // =====================================================

    /**
     * Registers a new Student account.
     *
     * Steps (matching FR-1.1 workflow exactly):
     * 1. Validate inputs
     * 2. Check for duplicate email → 409 if exists
     * 3. Hash password with bcrypt
     * 4. Create user with PENDING_VERIFICATION status and role=STUDENT
     * 5. Generate UUID verification token (24hr TTL)
     * 6. Send verification email
     */
    public void registerStudent(RegisterRequest request) {

        // Step 1: Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Passwords do not match"
            );
        }

        // Step 2: Check for duplicate email → 409 Conflict (FR-1.1)
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email is already registered."
            );
        }

        // Optional: validate phone format if provided
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (!request.getPhoneNumber().matches("^(07\\d{8}|7\\d{8})$")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phone number must start with 07 and be 10 digits, or start with 7 and be 9 digits"
                );
            }
        }

        // Step 3 & 4: Hash password and create user record
        // Role is ALWAYS set to STUDENT server-side — client cannot override this (FR-1.1)
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(User.Role.STUDENT)
                .accountStatus(User.AccountStatus.PENDING_VERIFICATION) // Cannot login yet
                .build();

        user = userRepository.save(user);
        logger.info("New student registered: {}", user.getEmail());

        // Step 5: Generate email verification token
        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusMinutes(emailVerificationExpiryMinutes))
                .used(false)
                .build();
        emailTokenRepository.save(verificationToken);

        // Step 6: Send verification email (runs in background thread via @Async)
        emailService.sendVerificationEmail(
                user.getEmail(), user.getFullName(), tokenValue
        );
    }

    // =====================================================
    // FR-1.1: EMAIL VERIFICATION
    // =====================================================

    /**
     * Verifies a user's email using the token from the verification link.
     * On success: account status becomes ACTIVE.
     */
    public void verifyEmail(String token) {

        // Find the token in database
        EmailVerificationToken verificationToken = emailTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid verification link."
                ));

        // Check if the token is still valid (not expired, not already used)
        if (!verificationToken.isValid()) {
            if (verificationToken.isExpired()) {
                throw new ResponseStatusException(
                        HttpStatus.GONE, "EXPIRED" // Frontend uses this to show "resend" button
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This link has already been used."
            );
        }

        // Activate the user account
        User user = verificationToken.getUser();
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        userRepository.save(user);

        // Mark token as used (single-use only)
        verificationToken.setUsed(true);
        emailTokenRepository.save(verificationToken);

        // Audit log
        saveAuditLog(AuditLog.ACTION_EMAIL_VERIFIED, null, user,
                "Email verified for: " + user.getEmail());

        logger.info("Email verified for user: {}", user.getEmail());
    }

    // =====================================================
    // FR-1.1: RESEND VERIFICATION EMAIL
    // =====================================================

    /**
     * Resends a new verification email.
     * The previous token remains in the database (unused) but the new one
     * supersedes it by TTL — only the newest valid token works.
     */
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account found with that email."
                ));

        if (user.getAccountStatus() == User.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This account is already verified."
            );
        }

        // Generate a new verification token
        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken newToken = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusMinutes(emailVerificationExpiryMinutes))
                .used(false)
                .build();
        emailTokenRepository.save(newToken);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), tokenValue);
        logger.info("Verification email resent to: {}", email);
    }

    // =====================================================
    // FR-1.2: LOGIN + JWT ISSUANCE
    // =====================================================

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * On success returns: accessToken, refreshToken, user info, role.
     * The frontend stores the accessToken and uses it in every API request.
     */
    public LoginResponse login(LoginRequest request) {

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password."
                ));

        // Verify password against the stored bcrypt hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid email or password."
            );
        }

        // Check account status before allowing login
        if (user.getAccountStatus() == User.AccountStatus.PENDING_VERIFICATION) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please verify your email before logging in."
            );
        }

        if (user.getAccountStatus() == User.AccountStatus.INACTIVE) {
            // FR-1.4: Deactivated users see this specific message
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Your account has been deactivated. Please contact the administrator."
            );
        }

        // Generate access token (short-lived: 1 hour)
        String accessToken = jwtUtils.generateAccessToken(user);

        // Generate refresh token (long-lived: 7 days) and persist it
        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        logger.info("User logged in: {} ({})", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .accountStatus(user.getAccountStatus().name())
                .build();
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    /**
     * Invalidates the user's refresh token on logout.
     * The access token will naturally expire after 1 hour.
     */
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    // =====================================================
    // FORGOT PASSWORD — Step 1: Request reset link
    // =====================================================

    /**
     * Sends a password reset email.
     * FR Security Note: The response is ALWAYS the same whether the email exists or not.
     * This prevents email enumeration attacks (discovering which emails are registered).
     */
    public void requestPasswordReset(ForgotPasswordRequest request) {

        // Intentionally don't throw if user not found — return same success message
        userRepository.findByEmail(request.getEmail().toLowerCase())
                .ifPresent(user -> {
                    String tokenValue = UUID.randomUUID().toString();
                    PasswordResetToken resetToken = PasswordResetToken.builder()
                            .user(user)
                            .token(tokenValue)
                            .expiresAt(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes))
                            .used(false)
                            .build();
                    passwordResetTokenRepository.save(resetToken);

                    emailService.sendPasswordResetEmail(
                            user.getEmail(), user.getFullName(), tokenValue
                    );

                    logger.info("Password reset requested for: {}", user.getEmail());
                });
        // No "else" — FRS says response is "vague for security"
    }

    // =====================================================
    // RESET PASSWORD — Step 2: Set new password
    // =====================================================

    /**
     * Validates the reset token and sets the new password.
     */
    public void resetPassword(ResetPasswordRequest request) {

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Passwords do not match."
            );
        }

        // Find the token
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid link."
                ));

        if (!resetToken.isValid()) {
            if (resetToken.isExpired()) {
                throw new ResponseStatusException(
                        HttpStatus.GONE, "This link has expired — request a new one."
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid link."
            );
        }

        // Update the password
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate the token
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all refresh tokens (force re-login after reset — security best practice)
        refreshTokenRepository.deleteAllByUserId(user.getId());

        // Audit log
        saveAuditLog(AuditLog.ACTION_PASSWORD_RESET, null, user,
                "Password reset via email link for: " + user.getEmail());

        logger.info("Password reset completed for: {}", user.getEmail());
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

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