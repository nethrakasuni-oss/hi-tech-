package com.hitech.lms.exam.repository;
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


import com.hitech.lms.exam.model.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    // Instructor/Admin: list exams for a course with optional status + search filter
    @Query("SELECT e FROM Exam e WHERE " +
           "(:courseId IS NULL OR e.course.id = :courseId) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:search IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%',:search,'%'))) " +
           "ORDER BY e.createdAt DESC")
    Page<Exam> findWithFilters(
        @Param("courseId") Long courseId,
        @Param("status")   Exam.ExamStatus status,
        @Param("search")   String search,
        Pageable pageable
    );

    // Student: active exams within the availability window for enrolled courses
    @Query("SELECT DISTINCT e FROM Exam e " +
           "JOIN Enrollment en ON en.course = e.course " +
           "WHERE en.student.id = :studentId AND en.status = 'ACTIVE' " +
           "AND e.status = 'ACTIVE' " +
           "AND (:courseId IS NULL OR e.course.id = :courseId)")
    List<Exam> findActiveExamsForStudent(
        @Param("studentId") Long studentId,
        @Param("courseId")  Long courseId
    );

    // Auto-deactivation: find ACTIVE exams whose window has closed
    @Query("SELECT e FROM Exam e WHERE e.status = 'ACTIVE' AND e.availableUntil < :now")
    List<Exam> findExpiredActiveExams(@Param("now") LocalDateTime now);

    // Count active exams for instructor's courses (dashboard stat)
    @Query("SELECT COUNT(DISTINCT e) FROM Exam e " +
           "JOIN e.course c JOIN c.instructors i " +
           "WHERE i.id = :instructorId AND e.status NOT IN ('ARCHIVED')")
    long countExamsForInstructor(@Param("instructorId") Long instructorId);

    // Admin dashboard: total active exams
    long countByStatus(Exam.ExamStatus status);
}
