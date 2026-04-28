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


import com.hitech.lms.exam.model.AttemptQuestionGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptQuestionGradeRepository extends JpaRepository<AttemptQuestionGrade, Long> {

    List<AttemptQuestionGrade> findByAttemptId(Long attemptId);

    Optional<AttemptQuestionGrade> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    // Count pending manual grades for an attempt
    long countByAttemptIdAndGradeStatus(Long attemptId, AttemptQuestionGrade.GradeStatus status);

    // For analytics: all grades for an exam's graded attempts
    @Query("SELECT g FROM AttemptQuestionGrade g " +
           "JOIN g.attempt a WHERE a.exam.id = :examId AND a.status = 'GRADED'")
    List<AttemptQuestionGrade> findAllGradesForExam(@Param("examId") Long examId);

    // Per-question correct count (difficulty analysis)
    @Query("SELECT g.question.id, COUNT(g) FROM AttemptQuestionGrade g " +
           "JOIN g.attempt a WHERE a.exam.id = :examId AND a.status = 'GRADED' " +
           "AND g.gradeStatus = 'CORRECT' GROUP BY g.question.id")
    List<Object[]> countCorrectPerQuestion(@Param("examId") Long examId);
}
