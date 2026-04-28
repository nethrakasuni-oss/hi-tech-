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
import java.util.List;

/** Class analytics dashboard data (FR-5.4) */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExamAnalyticsResponse {
    private Long examId;
    private String examTitle;
    private String courseTitle;
    private int totalSubmissions;
    private boolean resultsReleased;

    // KPI cards
    private BigDecimal averageScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private BigDecimal passRate;

    // Score distribution (histogram buckets: 0-10, 10-20, ... 90-100)
    private List<DistributionBucket> scoreDistribution;

    // Per-question difficulty
    private List<QuestionDifficulty> questionDifficulty;

    // At-risk students
    private List<AtRiskStudent> atRiskStudents;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DistributionBucket {
        private String label;  // "0-10", "10-20", etc.
        private int count;
        private BigDecimal percentage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionDifficulty {
        private Long questionId;
        private int displayOrder;
        private String questionText;
        private BigDecimal correctPct;   // % of students who got it right
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AtRiskStudent {
        private Long studentId;
        private String studentName;
        private String studentEmail;
        private BigDecimal score;
        private BigDecimal percentage;
        private Long attemptId;
    }
}
