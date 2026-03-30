package com.hitech.lms.support.service;
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
import com.hitech.lms.support.repository.KnowledgeBaseArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@Transactional
public class KnowledgeBaseService {

    @Autowired private KnowledgeBaseArticleRepository articleRepository;

    // ---- CREATE ----
    public ArticleResponse createArticle(CreateArticleRequest req, User author) {
        validateTitleUnique(req.getTitle(), req.getCategory(), null);

        KnowledgeBaseArticle.ArticleStatus status = req.getStatus() != null
                ? req.getStatus() : KnowledgeBaseArticle.ArticleStatus.DRAFT;

        if (status == KnowledgeBaseArticle.ArticleStatus.PUBLISHED
                && req.getContent().length() < 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Published articles must contain at least 100 characters.");

        KnowledgeBaseArticle article = KnowledgeBaseArticle.builder()
                .title(req.getTitle().trim())
                .category(req.getCategory())
                .content(req.getContent())
                .tags(req.getTags())
                .status(status)
                .version(1)
                .author(author)
                .build();

        return mapToResponse(articleRepository.save(article));
    }

    // ---- UPDATE ----
    public ArticleResponse updateArticle(Long id, CreateArticleRequest req, User editor) {
        KnowledgeBaseArticle article = findById(id);

        if (article.getStatus() == KnowledgeBaseArticle.ArticleStatus.RETIRED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retired articles cannot be edited.");

        validateTitleUnique(req.getTitle(), req.getCategory(), id);

        KnowledgeBaseArticle.ArticleStatus newStatus = req.getStatus() != null
                ? req.getStatus() : article.getStatus();

        if (newStatus == KnowledgeBaseArticle.ArticleStatus.PUBLISHED
                && req.getContent().length() < 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Published articles must contain at least 100 characters.");

        article.setTitle(req.getTitle().trim());
        article.setCategory(req.getCategory());
        article.setContent(req.getContent());
        article.setTags(req.getTags());
        article.setStatus(newStatus);
        article.setVersion(article.getVersion() + 1);

        return mapToResponse(articleRepository.save(article));
    }

    // ---- RETIRE (soft-delete) ----
    public ArticleResponse retireArticle(Long id, User admin) {
        KnowledgeBaseArticle article = findById(id);
        article.setStatus(KnowledgeBaseArticle.ArticleStatus.RETIRED);
        return mapToResponse(articleRepository.save(article));
    }

    // ---- RATE ----
    public void rateArticle(Long id, boolean helpful) {
        KnowledgeBaseArticle article = findById(id);
        if (helpful) article.setHelpfulYes(article.getHelpfulYes() + 1);
        else         article.setHelpfulNo(article.getHelpfulNo() + 1);
        articleRepository.save(article);
    }

    // ---- INCREMENT VIEW ----
    public ArticleResponse getArticle(Long id, User viewer) {
        KnowledgeBaseArticle article = findById(id);
        boolean isStaff = viewer.getRole() == User.Role.SUPPORT_STAFF
                || viewer.getRole() == User.Role.ADMIN;
        if (!isStaff && article.getStatus() != KnowledgeBaseArticle.ArticleStatus.PUBLISHED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found.");
        article.setViewCount(article.getViewCount() + 1);
        articleRepository.save(article);
        return mapToResponse(article);
    }

    // ---- SEARCH (public: PUBLISHED only) ----
    @Transactional(readOnly = true)
    public Page<ArticleResponse> searchPublished(String search, String category, int page, int size) {
        KnowledgeBaseArticle.ArticleCategory cat = (category != null && !category.isBlank())
                ? KnowledgeBaseArticle.ArticleCategory.valueOf(category) : null;
        return articleRepository.searchPublished(search, cat, PageRequest.of(page, size))
                .map(this::mapToResponse);
    }

    // ---- LIST ALL (staff/admin management) ----
    @Transactional(readOnly = true)
    public Page<ArticleResponse> listAll(String status, String category,
                                         String search, int page, int size) {
        KnowledgeBaseArticle.ArticleStatus st = (status != null && !status.isBlank())
                ? KnowledgeBaseArticle.ArticleStatus.valueOf(status) : null;
        KnowledgeBaseArticle.ArticleCategory cat = (category != null && !category.isBlank())
                ? KnowledgeBaseArticle.ArticleCategory.valueOf(category) : null;
        return articleRepository.searchAll(st, cat, search, PageRequest.of(page, size))
                .map(this::mapToResponse);
    }

    private KnowledgeBaseArticle findById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found."));
    }

    private void validateTitleUnique(String title, KnowledgeBaseArticle.ArticleCategory cat, Long excludeId) {
        boolean exists = excludeId == null
                ? articleRepository.existsByTitleAndCategory(title, cat)
                : articleRepository.existsByTitleAndCategoryAndIdNot(title, cat, excludeId);
        if (exists)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An article with this title already exists in the selected category.");
    }

    private ArticleResponse mapToResponse(KnowledgeBaseArticle a) {
        return ArticleResponse.builder()
                .id(a.getId()).title(a.getTitle()).category(a.getCategory().name())
                .content(a.getContent()).tags(a.getTags()).status(a.getStatus().name())
                .version(a.getVersion()).viewCount(a.getViewCount())
                .helpfulYes(a.getHelpfulYes()).helpfulNo(a.getHelpfulNo())
                .authorName(a.getAuthor().getFullName())
                .createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt()).build();
    }
}