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


import com.hitech.lms.course.model.Course;
import com.hitech.lms.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByCourseCode(String courseCode);

    // ---- Admin / Instructor: search all courses with filters ----
    @Query("SELECT c FROM Course c WHERE " +
           "(:search IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.courseCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:programId IS NULL OR c.program.id = :programId)")
    Page<Course> searchCourses(
        @Param("search") String search,
        @Param("status") Course.CourseStatus status,
        @Param("programId") Long programId,
        Pageable pageable
    );

    // ---- Instructor: only courses assigned to them ----
    @Query("SELECT c FROM Course c JOIN c.instructors i WHERE i = :instructor " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR c.status = :status)")
    Page<Course> searchCoursesByInstructor(
        @Param("instructor") User instructor,
        @Param("search") String search,
        @Param("status") Course.CourseStatus status,
        Pageable pageable
    );

    // ---- Student: browse ACTIVE courses in catalog ----
    @Query("SELECT c FROM Course c WHERE c.status = 'ACTIVE' " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:programId IS NULL OR c.program.id = :programId)")
    Page<Course> searchActiveCourses(
        @Param("search") String search,
        @Param("programId") Long programId,
        Pageable pageable
    );

    // ---- Student: courses they are enrolled in ----
    @Query("SELECT DISTINCT c FROM Course c " +
           "JOIN Enrollment e ON e.course = c " +
           "WHERE e.student.id = :studentId AND e.status = 'ACTIVE'")
    List<Course> findEnrolledCoursesByStudent(@Param("studentId") Long studentId);

    // ---- Dashboard stats ----
    long countByStatus(Course.CourseStatus status);

    // ---- Instructor assigned to at least one course ----
    @Query("SELECT DISTINCT c FROM Course c JOIN c.instructors i WHERE i.id = :instructorId")
    List<Course> findByInstructorId(@Param("instructorId") Long instructorId);
}
