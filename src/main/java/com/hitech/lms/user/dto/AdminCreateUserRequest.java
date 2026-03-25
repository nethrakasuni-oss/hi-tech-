package com.hitech.lms.user.dto;


import com.hitech.lms.auth.model.User;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class AdminCreateUserRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotNull(message = "Role is required")
    private User.Role role;

    private String phoneNumber;
    private String temporaryPassword;
}
