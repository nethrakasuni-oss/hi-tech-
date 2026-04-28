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

/** Exam attempt with questions for the taking interface (FR-5.2) */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttemptResponse {
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private Integer timeLimitMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private String status;
    private int totalQuestions;
    private long answeredCount;
    private List<AttemptQuestionDto> questions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AttemptQuestionDto {
        private Long questionId;
        private int displayOrder;
        private String questionType;
        private String questionText;
        private BigDecimal points;
        private List<OptionDto> options; // only for MCQ
        private String allowedFileTypes;
        private Integer maxFileSizeMb;
        // Pre-filled if resuming
        private String savedAnswer;
        private boolean flagged;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionDto {
        private Long optionId;
        private String optionText;
        // isCorrect NOT included here — never send correct answer to student
    }
}
