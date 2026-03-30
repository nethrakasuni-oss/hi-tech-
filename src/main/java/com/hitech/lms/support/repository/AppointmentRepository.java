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


import com.hitech.lms.support.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByStudentIdOrderByAppointmentDateDesc(Long studentId, Pageable pageable);

    Page<Appointment> findByInstructorIdOrderByAppointmentDateAsc(Long instructorId, Pageable pageable);

    // Check for slot conflict
    @Query("SELECT a FROM Appointment a WHERE a.instructor.id = :instructorId " +
            "AND a.appointmentDate = :date AND a.status IN ('PENDING','CONFIRMED') " +
            "AND a.startTime < :endTime AND a.endTime > :startTime " +
            "AND (:excludeId IS NULL OR a.id <> :excludeId)")
    List<Appointment> findConflicts(
            @Param("instructorId") Long instructorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    // Student active booking count
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.student.id = :studentId " +
            "AND a.status IN ('PENDING','CONFIRMED')")
    long countActiveByStudent(@Param("studentId") Long studentId);

    // Instructor upcoming
    @Query("SELECT a FROM Appointment a WHERE a.instructor.id = :instructorId " +
            "AND a.appointmentDate >= :from AND a.status IN ('PENDING','CONFIRMED') " +
            "ORDER BY a.appointmentDate ASC, a.startTime ASC")
    List<Appointment> findUpcomingForInstructor(
            @Param("instructorId") Long instructorId,
            @Param("from") LocalDate from);
}