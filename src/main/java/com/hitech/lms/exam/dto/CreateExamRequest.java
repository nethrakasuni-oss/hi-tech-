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

import com.hitech.lms.exam.model.Exam;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** POST /api/exams — create or update exam (FR-5.1) */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateExamRequest {
    @NotNull(message = "Course is required") private Long courseId;
    @NotBlank(message = "Title is required") @Size(max = 200) private String title;
    private String description;
    @NotNull @DecimalMin("0.01") private BigDecimal totalMarks;
    @NotNull @DecimalMin("1") @DecimalMax("100") private BigDecimal passingGradePct;
    private Integer timeLimitMinutes;
    private Integer maxAttempts;
    private LocalDateTime availableFrom;
    private LocalDateTime availableUntil;
    private Exam.ResultRelease resultRelease;
    private boolean useRandomization;
    private List<QuestionRequest> questions;
    private List<PoolConfigRequest> poolConfigs;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class QuestionRequest {
        private Long id; // null for new questions
        private String questionType; // MCQ/SHORT_ANSWER/ESSAY/FILE_UPLOAD
        @NotBlank private String questionText;
        @NotNull @DecimalMin("0.01") private BigDecimal points;
        private String poolName;
        private int sortOrder;
        private String rubric;
        private String allowedFileTypes;
        private Integer maxFileSizeMb;
        private String shortAnswerMethod;
        private String expectedAnswer;
        private List<OptionRequest> options;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OptionRequest {
        private Long id;
        private String optionText;
        private boolean correct;
        private int sortOrder;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PoolConfigRequest {
        private String poolName;
        private int drawCount;
    }
}
