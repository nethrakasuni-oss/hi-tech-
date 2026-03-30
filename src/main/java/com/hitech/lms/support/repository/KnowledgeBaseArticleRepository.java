package com.hitech.lms.support.repository;
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


import com.hitech.lms.support.model.KnowledgeBaseArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KnowledgeBaseArticleRepository extends JpaRepository<KnowledgeBaseArticle, Long> {

    // Public search: PUBLISHED articles only
    @Query("SELECT a FROM KnowledgeBaseArticle a WHERE a.status = 'PUBLISHED' " +
            "AND (:search IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(a.content) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(a.tags) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:category IS NULL OR a.category = :category) " +
            "ORDER BY a.viewCount DESC")
    Page<KnowledgeBaseArticle> searchPublished(
            @Param("search") String search,
            @Param("category") KnowledgeBaseArticle.ArticleCategory category,
            Pageable pageable);

    // Staff/admin: all articles with filters
    @Query("SELECT a FROM KnowledgeBaseArticle a WHERE " +
            "(:status IS NULL OR a.status = :status) " +
            "AND (:category IS NULL OR a.category = :category) " +
            "AND (:search IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "ORDER BY a.updatedAt DESC")
    Page<KnowledgeBaseArticle> searchAll(
            @Param("status") KnowledgeBaseArticle.ArticleStatus status,
            @Param("category") KnowledgeBaseArticle.ArticleCategory category,
            @Param("search") String search,
            Pageable pageable);

    // Stale articles (not updated in N days)
    @Query("SELECT a FROM KnowledgeBaseArticle a WHERE a.status = 'PUBLISHED' " +
            "AND a.updatedAt < :before ORDER BY a.updatedAt ASC")
    List<KnowledgeBaseArticle> findStale(@Param("before") LocalDateTime before);

    boolean existsByTitleAndCategory(String title, KnowledgeBaseArticle.ArticleCategory category);

    boolean existsByTitleAndCategoryAndIdNot(String title, KnowledgeBaseArticle.ArticleCategory category, Long id);
}