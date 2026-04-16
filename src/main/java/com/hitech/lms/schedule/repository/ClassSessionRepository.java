package com.hitech.lms.schedule.repository;
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


import com.hitech.lms.schedule.model.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    // ---- Admin: all sessions in a date range ----
    @Query("SELECT s FROM ClassSession s WHERE s.sessionDate BETWEEN :from AND :to " +
           "AND (:courseId IS NULL OR s.course.id = :courseId) " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findAllInRange(
        @Param("from")     LocalDate from,
        @Param("to")       LocalDate to,
        @Param("courseId") Long courseId
    );

    // ---- Instructor: sessions for their assigned courses in a date range ----
    @Query("SELECT s FROM ClassSession s WHERE s.instructor.id = :instructorId " +
           "AND s.sessionDate BETWEEN :from AND :to " +
           "AND (:courseId IS NULL OR s.course.id = :courseId) " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findByInstructorInRange(
        @Param("instructorId") Long instructorId,
        @Param("from")         LocalDate from,
        @Param("to")           LocalDate to,
        @Param("courseId")     Long courseId
    );

    // ---- Student: sessions for their enrolled courses in a date range ----
    @Query("SELECT DISTINCT s FROM ClassSession s " +
           "JOIN Enrollment e ON e.course = s.course " +
           "WHERE e.student.id = :studentId AND e.status = 'ACTIVE' " +
           "AND s.sessionDate BETWEEN :from AND :to " +
           "AND (:courseId IS NULL OR s.course.id = :courseId) " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findByStudentEnrollmentInRange(
        @Param("studentId") Long studentId,
        @Param("from")      LocalDate from,
        @Param("to")        LocalDate to,
        @Param("courseId")  Long courseId
    );

    // ---- Upcoming sessions (next N sessions from today) ----
    @Query("SELECT s FROM ClassSession s WHERE s.sessionDate >= :today " +
           "AND s.status = 'SCHEDULED' " +
           "AND (:instructorId IS NULL OR s.instructor.id = :instructorId) " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findUpcomingForInstructor(
        @Param("today")        LocalDate today,
        @Param("instructorId") Long instructorId,
        org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT DISTINCT s FROM ClassSession s " +
           "JOIN Enrollment e ON e.course = s.course " +
           "WHERE e.student.id = :studentId AND e.status = 'ACTIVE' " +
           "AND s.sessionDate >= :today AND s.status = 'SCHEDULED' " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findUpcomingForStudent(
        @Param("studentId") Long studentId,
        @Param("today")     LocalDate today,
        org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT s FROM ClassSession s WHERE s.sessionDate >= :today " +
           "AND s.status = 'SCHEDULED' " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<ClassSession> findUpcomingAll(
        @Param("today") LocalDate today,
        org.springframework.data.domain.Pageable pageable
    );

    // ---- Conflict check: does an instructor have an overlapping session? ----
    @Query("SELECT s FROM ClassSession s WHERE s.instructor.id = :instructorId " +
           "AND s.sessionDate = :date AND s.status = 'SCHEDULED' " +
           "AND s.id <> :excludeId " +
           "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<ClassSession> findConflicts(
        @Param("instructorId") Long instructorId,
        @Param("date")         LocalDate date,
        @Param("startTime")    LocalTime startTime,
        @Param("endTime")      LocalTime endTime,
        @Param("excludeId")    Long excludeId
    );

    // ---- Get all sessions in a recurrence group ----
    List<ClassSession> findByRecurrenceGroupIdOrderBySessionDateAsc(Long recurrenceGroupId);

    // ---- Get future sessions in a recurrence group (for "edit this and future") ----
    @Query("SELECT s FROM ClassSession s WHERE s.recurrenceGroup.id = :groupId " +
           "AND s.sessionDate >= :from ORDER BY s.sessionDate ASC")
    List<ClassSession> findFutureInGroup(
        @Param("groupId") Long groupId,
        @Param("from")    LocalDate from
    );

    // ---- Dashboard: count upcoming sessions this week for a course's instructor ----
    @Query("SELECT COUNT(s) FROM ClassSession s WHERE s.instructor.id = :instructorId " +
           "AND s.sessionDate BETWEEN :from AND :to AND s.status = 'SCHEDULED'")
    long countUpcomingThisWeekForInstructor(
        @Param("instructorId") Long instructorId,
        @Param("from")         LocalDate from,
        @Param("to")           LocalDate to
    );

    @Query("SELECT COUNT(DISTINCT s) FROM ClassSession s " +
           "JOIN Enrollment e ON e.course = s.course " +
           "WHERE e.student.id = :studentId AND e.status = 'ACTIVE' " +
           "AND s.sessionDate BETWEEN :from AND :to AND s.status = 'SCHEDULED'")
    long countUpcomingThisWeekForStudent(
        @Param("studentId") Long studentId,
        @Param("from")      LocalDate from,
        @Param("to")        LocalDate to
    );
}
