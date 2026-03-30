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


import com.hitech.lms.support.model.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    // Count by year for ticket number generation
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE YEAR(t.createdAt) = :year")
    long countByYear(@Param("year") int year);

    // Student: their own tickets
    Page<SupportTicket> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    @Query("SELECT t FROM SupportTicket t WHERE t.student.id = :studentId " +
            "AND (:status IS NULL OR t.status = :status) ORDER BY t.createdAt DESC")
    Page<SupportTicket> findByStudentWithFilter(
            @Param("studentId") Long studentId,
            @Param("status") SupportTicket.TicketStatus status,
            Pageable pageable);

    // Staff: all tickets with filters
    @Query("SELECT t FROM SupportTicket t WHERE " +
            "(:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:search IS NULL OR LOWER(t.student.fullName) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(t.subject) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "ORDER BY t.createdAt DESC")
    Page<SupportTicket> findAllWithFilters(
            @Param("status") SupportTicket.TicketStatus status,
            @Param("category") SupportTicket.TicketCategory category,
            @Param("search") String search,
            Pageable pageable);

    // Stats
    long countByStatus(SupportTicket.TicketStatus status);

    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.status = 'RESOLVED' " +
            "AND t.resolvedAt >= :start AND t.resolvedAt <= :end")
    long countResolvedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}