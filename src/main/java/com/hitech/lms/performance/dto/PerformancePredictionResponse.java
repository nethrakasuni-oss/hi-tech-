package com.hitech.lms.performance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformancePredictionResponse {
    private Long courseId;
    private String courseCode;
    private String courseTitle;
    private List<StudentPrediction> predictions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentPrediction {
        private Long studentId;
        private String studentName;
        private String studentEmail;
        private String enrollmentStatus;
        private String performanceCategory;
        private BigDecimal confidence;
        private BigDecimal attendancePercentage;
        private BigDecimal averageExamPercentage;
        private Integer supportTicketCount;
        private Integer lastActivityDaysAgo;
        private Map<String, Object> features;
    }
}
