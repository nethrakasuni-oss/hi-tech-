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


import com.hitech.lms.exam.model.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findByExamIdOrderBySortOrderAsc(Long examId);

    // Sum all question points for an exam (used for total_marks validation)
    @Query("SELECT COALESCE(SUM(q.points), 0) FROM ExamQuestion q WHERE q.exam.id = :examId")
    BigDecimal sumPointsByExamId(@Param("examId") Long examId);

    // Get distinct pool names for an exam
    @Query("SELECT DISTINCT q.poolName FROM ExamQuestion q WHERE q.exam.id = :examId AND q.poolName IS NOT NULL")
    List<String> findDistinctPoolNames(@Param("examId") Long examId);

    // Count questions in a pool
    @Query("SELECT COUNT(q) FROM ExamQuestion q WHERE q.exam.id = :examId AND q.poolName = :poolName")
    long countByExamIdAndPoolName(@Param("examId") Long examId, @Param("poolName") String poolName);

    // Questions by pool (for randomization draw)
    List<ExamQuestion> findByExamIdAndPoolName(Long examId, String poolName);
}
