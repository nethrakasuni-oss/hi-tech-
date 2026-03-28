package com.hitech.lms.course.dto;
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


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// =============================================
// RESPONSE DTOs
// =============================================

/**
 * Full course details — returned when fetching a single course
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CourseDetailResponse {
    private Long id;
    private String title;
    private String courseCode;
    private String description;
    private Long programId;
    private String programName;
    private Integer durationValue;
    private String durationUnit;
    private BigDecimal courseFee;
    private String enrollmentType;
    private Integer maxStudents;
    private String thumbnailUrl;
    private String currentProgress;
    private String status;
    private Long enrolledCount;
    private boolean isEnrolled;             // true if the requesting student is enrolled
    private List<InstructorSummary> instructors;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InstructorSummary {
        private Long id;
        private String fullName;
        private String email;
        private String profilePhotoUrl;
    }
}
