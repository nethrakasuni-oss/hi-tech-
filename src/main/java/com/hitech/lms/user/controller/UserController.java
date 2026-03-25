package com.hitech.lms.user.controller;






import com.hitech.lms.dto.ApiResponse;

import com.hitech.lms.user.dto.*;

import com.hitech.lms.auth.model.User;
import com.hitech.lms.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * UserController — Profile management for any authenticated user.
 *
 * @AuthenticationPrincipal User currentUser
 * → Spring injects the currently logged-in User object automatically.
 *   This comes from JwtAuthenticationFilter which sets it in SecurityContext.
 *
 * Base path: /api/users
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // ---- GET /api/users/me ----
    // Returns the currently logged-in user's profile
    // Used by the frontend to populate the dashboard header and profile page
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal User currentUser) {

        UserProfileResponse profile = userService.getProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched.", profile));
    }

    // ---- PATCH /api/users/profile ----
    // FR-1.3: Update own profile (name, phone, bio, language)
    // Note: Email is read-only and cannot be changed
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse updated = userService.updateProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.", updated));
    }

    // ---- POST /api/users/profile/change-password ----
    // FR-1.3: Change password — requires current password
    @PostMapping("/profile/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully."));
    }
}