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


import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * EmailService — Handles all outbound emails for the LMS.
 *
 * Uses @Async so email sending does NOT block the HTTP response.
 * The user gets a response immediately; the email is sent in the background.
 *
 * FR-1.1: Sends email verification link after registration
 * FR-1.1: Sends forgot password reset link
 * FR-1.1: Retries 3 times on failure (handled by Spring Retry — simplified here with try/catch)
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ---- Email: Verification Link ----

    /**
     * Sends a verification email after a new student registers.
     * The link points to the frontend verification page with the token.
     * @Async means this runs in a background thread
     */
    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        try {
            String verifyLink = frontendUrl + "/verify-email.html?token=" + token;

            String htmlContent = buildVerificationEmailHtml(fullName, verifyLink);

            sendHtmlEmail(
                    toEmail,
                    "Verify Your Hi-Tech Institute LMS Account",
                    htmlContent
            );

            logger.info("Verification email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            // In production: retry logic + admin alert would go here
        }
    }

    // ---- Email: Password Reset Link ----

    /**
     * Sends a password reset link to the user's email.
     * The link expires in 60 minutes (set in application.properties).
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password.html?token=" + token;

            String htmlContent = buildPasswordResetEmailHtml(fullName, resetLink);

            sendHtmlEmail(
                    toEmail,
                    "Reset Your Hi-Tech Institute LMS Password",
                    htmlContent
            );

            logger.info("Password reset email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ---- Email: Welcome / Account Created by Admin ----

    /**
     * Sent when Admin creates an Instructor or Admin account.
     * Tells the new user that their account is ready.
     */
    @Async
    public void sendAccountCreatedEmail(String toEmail, String fullName,
                                        String role, String temporaryPassword) {
        try {
            String loginLink = frontendUrl + "/login.html";
            String htmlContent = buildAccountCreatedEmailHtml(
                    fullName, role, toEmail, temporaryPassword, loginLink
            );

            sendHtmlEmail(
                    toEmail,
                    "Your Hi-Tech Institute LMS Account Has Been Created",
                    htmlContent
            );

            logger.info("Account creation email sent to: {}", toEmail);

        } catch (Exception e) {
            logger.error("Failed to send account creation email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ---- Email: Deactivation Notification ----

    @Async
    public void sendAccountDeactivationEmail(String toEmail, String fullName) {
        try {
            String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>"
                    + "<h2 style='color: #e53e3e;'>Account Deactivated</h2>"
                    + "<p>Dear " + fullName + ",</p>"
                    + "<p>Your Hi-Tech Institute LMS account has been deactivated by an administrator.</p>"
                    + "<p>If you believe this is a mistake, please contact your administrator.</p>"
                    + "<p>Best regards,<br>Hi-Tech Institute LMS Team</p>"
                    + "</div>";

            sendHtmlEmail(toEmail, "Your LMS Account Has Been Deactivated", htmlContent);
        } catch (Exception e) {
            logger.error("Failed to send deactivation email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ---- Private Helper Methods ----

    /**
     * Core method that actually sends an HTML email via JavaMailSender.
     */
    private void sendHtmlEmail(String to, String subject, String htmlBody)
            throws MessagingException, java.io.UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, "Hi-Tech Institute LMS");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = isHtml

        mailSender.send(message);
    }

    // ---- HTML Email Templates ----

    private String buildVerificationEmailHtml(String fullName, String verifyLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body>"
                + "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; "
                + "border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>"

                // Header
                + "<div style='background: linear-gradient(135deg, #1a365d 0%, #2b6cb0 100%); "
                + "padding: 30px; text-align: center;'>"
                + "<h1 style='color: white; margin: 0; font-size: 24px;'>Hi-Tech Institute LMS</h1>"
                + "</div>"

                // Body
                + "<div style='padding: 40px 30px;'>"
                + "<h2 style='color: #2d3748; margin-top: 0;'>Verify Your Email Address</h2>"
                + "<p style='color: #4a5568;'>Dear <strong>" + fullName + "</strong>,</p>"
                + "<p style='color: #4a5568;'>Thank you for registering. "
                + "Please verify your email address by clicking the button below:</p>"

                // Button
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + verifyLink + "' style='background-color: #2b6cb0; color: white; "
                + "padding: 14px 32px; text-decoration: none; border-radius: 6px; "
                + "font-size: 16px; font-weight: bold; display: inline-block;'>"
                + "Verify Email Address</a>"
                + "</div>"

                + "<p style='color: #718096; font-size: 14px;'>"
                + "⏰ This link expires in <strong>24 hours</strong>.</p>"
                + "<p style='color: #718096; font-size: 14px;'>"
                + "If you didn't register, you can safely ignore this email.</p>"

                // Link fallback
                + "<p style='color: #718096; font-size: 12px; word-break: break-all;'>"
                + "Or copy this link: " + verifyLink + "</p>"
                + "</div>"

                // Footer
                + "<div style='background: #f7fafc; padding: 20px; text-align: center; "
                + "border-top: 1px solid #e2e8f0;'>"
                + "<p style='color: #a0aec0; font-size: 12px; margin: 0;'>"
                + "© 2026 Hi-Tech Institute. All rights reserved.</p>"
                + "</div>"
                + "</div></body></html>";
    }

    private String buildPasswordResetEmailHtml(String fullName, String resetLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body>"
                + "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; "
                + "border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>"

                + "<div style='background: linear-gradient(135deg, #1a365d 0%, #2b6cb0 100%); "
                + "padding: 30px; text-align: center;'>"
                + "<h1 style='color: white; margin: 0; font-size: 24px;'>Hi-Tech Institute LMS</h1>"
                + "</div>"

                + "<div style='padding: 40px 30px;'>"
                + "<h2 style='color: #2d3748; margin-top: 0;'>Password Reset Request</h2>"
                + "<p style='color: #4a5568;'>Dear <strong>" + fullName + "</strong>,</p>"
                + "<p style='color: #4a5568;'>We received a request to reset your password. "
                + "Click the button below to set a new password:</p>"

                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + resetLink + "' style='background-color: #e53e3e; color: white; "
                + "padding: 14px 32px; text-decoration: none; border-radius: 6px; "
                + "font-size: 16px; font-weight: bold; display: inline-block;'>"
                + "Reset My Password</a>"
                + "</div>"

                + "<p style='color: #718096; font-size: 14px;'>"
                + "⏰ This link expires in <strong>60 minutes</strong>.</p>"
                + "<p style='color: #718096; font-size: 14px;'>"
                + "If you didn't request a password reset, please ignore this email. "
                + "Your password will not be changed.</p>"
                + "<p style='color: #718096; font-size: 12px; word-break: break-all;'>"
                + "Or copy: " + resetLink + "</p>"
                + "</div>"

                + "<div style='background: #f7fafc; padding: 20px; text-align: center; "
                + "border-top: 1px solid #e2e8f0;'>"
                + "<p style='color: #a0aec0; font-size: 12px; margin: 0;'>"
                + "© 2026 Hi-Tech Institute. All rights reserved.</p>"
                + "</div>"
                + "</div></body></html>";
    }

    private String buildAccountCreatedEmailHtml(String fullName, String role,
                                                String email, String tempPassword,
                                                String loginLink) {
        String passwordSection = (tempPassword != null)
                ? "<p>Your temporary password is: <strong style='background:#f7fafc;padding:4px 8px;"
                + "border-radius:4px;font-family:monospace;'>" + tempPassword + "</strong></p>"
                + "<p style='color:#e53e3e;font-size:13px;'>⚠️ Please change your password after first login.</p>"
                : "<p>You will receive a separate email with a link to set your password.</p>";

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body>"
                + "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; "
                + "border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;'>"
                + "<div style='background: linear-gradient(135deg, #1a365d 0%, #2b6cb0 100%); "
                + "padding: 30px; text-align: center;'>"
                + "<h1 style='color: white; margin: 0; font-size: 24px;'>Hi-Tech Institute LMS</h1>"
                + "</div>"
                + "<div style='padding: 40px 30px;'>"
                + "<h2 style='color: #2d3748; margin-top: 0;'>Welcome to Hi-Tech Institute LMS!</h2>"
                + "<p>Dear <strong>" + fullName + "</strong>,</p>"
                + "<p>An administrator has created a <strong>" + role + "</strong> account for you.</p>"
                + "<p><strong>Login Email:</strong> " + email + "</p>"
                + passwordSection
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + loginLink + "' style='background-color: #2b6cb0; color: white; "
                + "padding: 14px 32px; text-decoration: none; border-radius: 6px; "
                + "font-size: 16px; font-weight: bold; display: inline-block;'>Login Now</a>"
                + "</div>"
                + "</div>"
                + "<div style='background: #f7fafc; padding: 20px; text-align: center; "
                + "border-top: 1px solid #e2e8f0;'>"
                + "<p style='color: #a0aec0; font-size: 12px; margin: 0;'>"
                + "© 2026 Hi-Tech Institute. All rights reserved.</p>"
                + "</div>"
                + "</div></body></html>";
    }

    /**
     * FR-3.2: Send payment confirmation email after successful payment.
     */
    public void sendPaymentConfirmationEmail(
            String toEmail, String studentName, String courseTitle,
            java.math.BigDecimal amount, String currency,
            String invoiceNumber, String gatewayReference) {

        try {
            String subject = "Payment Confirmed — " + courseTitle;
            String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:32px;background:#f8fafc;border-radius:12px;">
              <div style="background:linear-gradient(135deg,#1e3a8a,#3b82f6);padding:28px 32px;border-radius:8px;margin-bottom:24px;">
                <h1 style="color:#fff;margin:0;font-size:22px;">Payment Confirmed ✓</h1>
                <p style="color:rgba(255,255,255,.8);margin:6px 0 0;">Hi-Tech Institute LMS</p>
              </div>
              <p style="color:#334155;font-size:15px;">Dear <strong>%s</strong>,</p>
              <p style="color:#334155;">Your payment has been successfully processed and your enrollment is now active.</p>
              <div style="background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:20px 0;">
                <div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #f1f5f9;">
                  <span style="color:#64748b;">Course</span>
                  <strong style="color:#1e293b;">%s</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #f1f5f9;">
                  <span style="color:#64748b;">Amount Paid</span>
                  <strong style="color:#1e293b;">%s %s</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #f1f5f9;">
                  <span style="color:#64748b;">Invoice Number</span>
                  <strong style="color:#1e293b;">%s</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:8px 0;">
                  <span style="color:#64748b;">Transaction Reference</span>
                  <code style="background:#f1f5f9;padding:2px 8px;border-radius:4px;font-size:12px;">%s</code>
                </div>
              </div>
              <p style="color:#334155;">You can download your invoice at any time from the <strong>Finance → My Payments</strong> section.</p>
              <p style="color:#94a3b8;font-size:12px;margin-top:24px;">Hi-Tech Institute LMS · This is an automated message.</p>
            </div>
            """.formatted(studentName, courseTitle,
                    currency, amount.toString(),
                    invoiceNumber, gatewayReference);

            sendHtmlEmail(toEmail, subject, body);
            logger.info("Payment confirmation email sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send payment confirmation email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * FR-4.3: Generic schedule notification email.
     * Sent to enrolled students and the instructor when sessions are
     * created, updated, or cancelled.
     */
    @Async
    public void sendScheduleNotificationEmail(
            String toEmail, String subject, String intro, String sessionInfo) {
        try {
            String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>"
                    + "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;"
                    + "border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;'>"
                    + "<div style='background:linear-gradient(135deg,#1e3a8a,#2563eb);"
                    + "padding:28px 32px;'>"
                    + "<h1 style='color:#fff;margin:0;font-size:20px;'>📅 Hi-Tech Institute LMS</h1>"
                    + "<p style='color:rgba(255,255,255,.8);margin:4px 0 0;font-size:13px;'>Schedule Notification</p>"
                    + "</div>"
                    + "<div style='padding:28px 32px;'>"
                    + "<p style='color:#334155;font-size:15px;margin-bottom:16px;'>" + intro + "</p>"
                    + "<div style='background:#f8fafc;border-left:4px solid #2563eb;"
                    + "border-radius:4px;padding:16px 20px;margin-bottom:20px;'>"
                    + sessionInfo
                    + "</div>"
                    + "<p style='color:#94a3b8;font-size:12px;'>This is an automated message from Hi-Tech Institute LMS.</p>"
                    + "</div></div></body></html>";

            sendHtmlEmail(toEmail, subject, html);
        } catch (Exception e) {
            logger.error("Failed to send schedule notification to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * FR-5.4: Notify student that exam results are now available.
     */
    @Async
    public void sendExamResultsNotification(
            String toEmail, String studentName, String examTitle, String courseTitle) {
        try {
            String subject = "Your Results Are Available — " + examTitle;
            String html = "<!DOCTYPE html><html><body>"
                    + "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;"
                    + "border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;'>"
                    + "<div style='background:linear-gradient(135deg,#1e3a8a,#2563eb);padding:28px 32px;'>"
                    + "<h1 style='color:#fff;margin:0;font-size:20px;'>📝 Hi-Tech Institute LMS</h1>"
                    + "<p style='color:rgba(255,255,255,.8);margin:4px 0 0;font-size:13px;'>Exam Results</p>"
                    + "</div>"
                    + "<div style='padding:28px 32px;'>"
                    + "<p style='color:#334155;font-size:15px;'>Dear <strong>" + studentName + "</strong>,</p>"
                    + "<p style='color:#334155;'>Your results for the following exam are now available:</p>"
                    + "<div style='background:#f0f9ff;border-left:4px solid #2563eb;border-radius:4px;"
                    + "padding:16px 20px;margin:20px 0;'>"
                    + "<div style='font-weight:700;font-size:16px;color:#1e293b;'>" + examTitle + "</div>"
                    + "<div style='font-size:13px;color:#64748b;margin-top:4px;'>Course: " + courseTitle + "</div>"
                    + "</div>"
                    + "<p style='color:#334155;'>Log in to the LMS and navigate to <strong>Exams</strong> "
                    + "to view your detailed score breakdown and instructor feedback.</p>"
                    + "<p style='color:#94a3b8;font-size:12px;margin-top:24px;'>"
                    + "Hi-Tech Institute LMS · This is an automated message.</p>"
                    + "</div></div></body></html>";

            sendHtmlEmail(toEmail, subject, html);
            logger.info("Exam results notification sent to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send exam results notification to {}: {}", toEmail, e.getMessage());
        }
    }
}