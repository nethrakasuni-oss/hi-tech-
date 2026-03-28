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


import com.hitech.lms.course.model.Course;
import com.hitech.lms.course.model.ContentNode;
import com.hitech.lms.course.model.CourseMaterial;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// =============================================
// REQUEST DTOs
// =============================================

/**
 * POST/PUT /api/courses — Create or update a course
 * FR-2.1: Course Creation & Management
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateCourseRequest {

    @NotBlank(message = "Course title is required")
    @Size(max = 200, message = "Course title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Course code is required")
    @Size(max = 20, message = "Course code cannot exceed 20 characters")
    private String courseCode;

    private String description;

    @NotNull(message = "Program is required")
    private Long programId;

    private Integer durationValue;

    private String durationUnit; // DAYS, WEEKS, MONTHS

    @NotNull(message = "Course fee is required")
    @DecimalMin(value = "0.00", message = "Course fee cannot be negative")
    private BigDecimal courseFee;

    private Course.EnrollmentType enrollmentType;

    private Integer maxStudents;

    private String thumbnailUrl;

    // IDs of instructors to assign
    private List<Long> instructorIds;
}
