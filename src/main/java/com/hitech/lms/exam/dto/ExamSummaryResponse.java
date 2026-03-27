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

/** Lightweight exam card for list views */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExamSummaryResponse {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String courseCode;
    private String title;
    private String description;
    private BigDecimal totalMarks;
    private BigDecimal passingGradePct;
    private Integer timeLimitMinutes;
    private Integer maxAttempts;
    private LocalDateTime availableFrom;
    private LocalDateTime availableUntil;
    private String resultRelease;
    private boolean resultsReleased;
    private String status;
    private boolean useRandomization;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Computed
    private long submissionCount;
    private long pendingGradeCount;
    private String windowStatus; // NOT_OPEN / OPEN / CLOSED
    // Student-specific
    private long attemptsUsed;
    private long attemptsRemaining; // -1 = unlimited
    private String studentAttemptStatus; // NOT_STARTED / IN_PROGRESS / SUBMITTED / GRADED
    private Long activeAttemptId;
}
