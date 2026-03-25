package com.hitech.lms.user.dto;

import com.hitech.lms.auth.model.User;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class AdminUpdateUserRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;
    private String phoneNumber;
    private User.Role role;
    private User.AccountStatus accountStatus;
}
