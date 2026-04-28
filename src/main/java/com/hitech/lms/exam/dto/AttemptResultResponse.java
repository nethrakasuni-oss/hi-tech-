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

/** Student's full result view (FR-5.4) */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttemptResultResponse {
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private String courseTitle;
    private BigDecimal totalScore;
    private BigDecimal totalMarks;
    private BigDecimal percentage;
    private boolean passed;
    private String status;
    private boolean resultsReleased;
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private List<QuestionResult> questionResults;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionResult {
        private Long questionId;
        private int displayOrder;
        private String questionType;
        private String questionText;
        private BigDecimal pointsEarned;
        private BigDecimal maxPoints;
        private String studentAnswer;
        private String correctAnswer; // shown for MCQ after release
        private String gradeStatus;
        private String feedback;
    }
}
