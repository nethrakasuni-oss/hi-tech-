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


import com.hitech.lms.exam.model.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    // Find all attempts by a student for an exam
    List<ExamAttempt> findByExamIdAndStudentIdOrderByStartedAtDesc(Long examId, Long studentId);

    // Find the active IN_PROGRESS attempt for a student
    Optional<ExamAttempt> findByExamIdAndStudentIdAndStatus(
        Long examId, Long studentId, ExamAttempt.AttemptStatus status
    );

    // Count completed attempts (SUBMITTED + GRADED) to enforce max_attempts
    @Query("SELECT COUNT(a) FROM ExamAttempt a WHERE a.exam.id = :examId " +
           "AND a.student.id = :studentId AND a.status IN ('SUBMITTED','GRADED')")
    long countCompletedAttempts(@Param("examId") Long examId, @Param("studentId") Long studentId);

    // All submissions for grading queue (instructor view)
    @Query("SELECT a FROM ExamAttempt a WHERE a.exam.id = :examId " +
           "AND a.status IN ('SUBMITTED','GRADED') " +
           "ORDER BY a.submittedAt ASC")
    List<ExamAttempt> findSubmittedByExamId(@Param("examId") Long examId);

    // Count submissions per exam
    @Query("SELECT COUNT(a) FROM ExamAttempt a WHERE a.exam.id = :examId AND a.status <> 'IN_PROGRESS'")
    long countSubmissions(@Param("examId") Long examId);

    // Count pending manual grades (grading queue)
    @Query("SELECT COUNT(DISTINCT a.id) FROM ExamAttempt a " +
           "JOIN AttemptQuestionGrade g ON g.attempt = a " +
           "WHERE a.exam.id = :examId AND g.gradeStatus = 'MANUAL_PENDING'")
    long countPendingGrades(@Param("examId") Long examId);

    // All submitted attempts for analytics (exclude IN_PROGRESS)
    @Query("SELECT a FROM ExamAttempt a WHERE a.exam.id = :examId AND a.status = 'GRADED'")
    List<ExamAttempt> findGradedByExamId(@Param("examId") Long examId);

    // Student: all attempts across all enrolled exams
    List<ExamAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    // Student: all attempts with released results (SUBMITTED or GRADED, resultsReleased = true)
    @Query("SELECT a FROM ExamAttempt a " +
           "JOIN FETCH a.exam e JOIN FETCH e.course " +
           "WHERE a.student.id = :studentId " +
           "AND a.status IN ('SUBMITTED','GRADED') " +
           "AND e.resultsReleased = true " +
           "ORDER BY a.submittedAt DESC")
    List<ExamAttempt> findReleasedResultsForStudent(@Param("studentId") Long studentId);
}
