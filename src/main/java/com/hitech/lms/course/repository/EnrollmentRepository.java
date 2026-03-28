package com.hitech.lms.course.repository;
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


import com.hitech.lms.course.model.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // Check if a specific enrollment exists (for duplicate prevention)
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    // Find a specific enrollment record
    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    // Check if a student has ACTIVE enrollment in a course (content access check)
    boolean existsByStudentIdAndCourseIdAndStatus(
        Long studentId, Long courseId, Enrollment.EnrollmentStatus status
    );

    // Paginated list of enrollments for a course (Admin: enrollment management screen)
    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId " +
           "AND (:search IS NULL OR LOWER(e.student.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.student.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR e.status = :status)")
    Page<Enrollment> findByCourseIdWithFilters(
        @Param("courseId") Long courseId,
        @Param("search") String search,
        @Param("status") Enrollment.EnrollmentStatus status,
        Pageable pageable
    );

    // Count active enrollments for a course (for capacity check)
    long countByCourseIdAndStatus(Long courseId, Enrollment.EnrollmentStatus status);

    // Count total active enrollments for a student
    long countByStudentIdAndStatus(Long studentId, Enrollment.EnrollmentStatus status);
}
