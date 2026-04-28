package com.hitech.lms.exam.dto;
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

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight summary of a single student attempt where exam results have been released.
 * Used by GET /api/exam-attempts/my-results (FR-5.4 View Results).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudentResultSummaryDto {
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private String courseTitle;
    private String courseCode;
    private BigDecimal totalScore;
    private BigDecimal totalMarks;
    private BigDecimal percentage;
    private boolean passed;
    private String status;          // SUBMITTED | GRADED
    private LocalDateTime submittedAt;
    private LocalDateTime availableFrom;
    private LocalDateTime availableUntil;
    private Integer timeLimitMinutes;
    private BigDecimal passingGradePct;
}
