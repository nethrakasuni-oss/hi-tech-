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

/** Grading queue for instructor manual grading (FR-5.3) */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GradingQueueResponse {
    private Long examId;
    private String examTitle;
    private String courseTitle;
    private long totalSubmissions;
    private long pendingCount;
    private long gradedCount;
    private List<SubmissionItem> submissions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubmissionItem {
        private Long attemptId;
        private Long studentId;
        private String studentName;
        private String studentEmail;
        private LocalDateTime submittedAt;
        private String attemptStatus;
        private long pendingQuestions;
        private BigDecimal currentScore;
        // Per-question items for grading view
        private List<QuestionToGrade> questionsToGrade;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionToGrade {
        private Long questionId;
        private String questionType;
        private String questionText;
        private String rubric;
        private BigDecimal maxPoints;
        private String studentAnswer;
        private String gradeStatus;
        private BigDecimal awardedScore;
        private String feedback;
    }
}
