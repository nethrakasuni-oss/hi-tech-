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
import java.util.List;

/** Full exam with questions (for builder and preview) */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExamDetailResponse {
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
    private List<QuestionDetail> questions;
    private List<PoolConfigDetail> poolConfigs;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionDetail {
        private Long id;
        private String questionType;
        private String questionText;
        private BigDecimal points;
        private String poolName;
        private int sortOrder;
        private String rubric;
        private String allowedFileTypes;
        private Integer maxFileSizeMb;
        private String shortAnswerMethod;
        private String expectedAnswer;
        private List<OptionDetail> options;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionDetail {
        private Long id;
        private String optionText;
        private boolean correct;
        private int sortOrder;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PoolConfigDetail {
        private String poolName;
        private int drawCount;
        private long questionCount;
    }
}
