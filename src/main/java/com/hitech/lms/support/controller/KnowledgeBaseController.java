package com.hitech.lms.support.controller;
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


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.support.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    @Autowired private KnowledgeBaseService kbService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ArticleResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success("Articles fetched.",
                kbService.searchPublished(search, category, page, size)));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<Page<ArticleResponse>>> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Articles fetched.",
                kbService.listAll(status, category, search, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ArticleResponse>> get(
            @PathVariable Long id, @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(ApiResponse.success("Article fetched.", kbService.getArticle(id, viewer)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> create(
            @Valid @RequestBody CreateArticleRequest req,
            @AuthenticationPrincipal User author) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Article created.", kbService.createArticle(req, author)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateArticleRequest req,
            @AuthenticationPrincipal User editor) {
        return ResponseEntity.ok(ApiResponse.success("Article updated.", kbService.updateArticle(id, req, editor)));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> retire(
            @PathVariable Long id, @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.success("Article retired.", kbService.retireArticle(id, admin)));
    }

    @PostMapping("/{id}/rate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> rate(
            @PathVariable Long id, @RequestParam boolean helpful) {
        kbService.rateArticle(id, helpful);
        return ResponseEntity.ok(ApiResponse.success("Rating recorded."));
    }
}