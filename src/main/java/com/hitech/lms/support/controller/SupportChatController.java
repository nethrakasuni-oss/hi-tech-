package com.hitech.lms.support.controller;

import com.hitech.lms.auth.model.User;
import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.support.dto.SupportChatRequest;
import com.hitech.lms.support.dto.SupportChatResponse;
import com.hitech.lms.support.service.SupportDeskChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/chat")
public class SupportChatController {

    @Autowired
    private SupportDeskChatService supportDeskChatService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SupportChatResponse>> chat(
            @Valid @RequestBody SupportChatRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                ApiResponse.success("Message processed.", supportDeskChatService.chat(request, user)));
    }
}
