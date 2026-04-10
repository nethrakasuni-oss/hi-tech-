package com.hitech.lms.schedule.dto;
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


import com.hitech.lms.schedule.model.ClassSession;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateSessionRequest {

    @NotBlank(message = "Session title is required")
    @Size(max = 200)
    private String title;

    @NotNull(message = "Instructor is required")
    private Long instructorId;

    @NotNull(message = "Date is required")
    private LocalDate sessionDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /**
     * ONLINE or PHYSICAL — determines which of the fields below are required.
     */
    @NotNull(message = "Class type is required")
    private ClassSession.ClassType classType = ClassSession.ClassType.ONLINE;

    // ---- ONLINE fields (required when classType = ONLINE) ----

    // Validated in ScheduleService#validateSessionMode
    private ClassSession.MeetingPlatform meetingPlatform;

    @Size(max = 500)
    private String meetingLink;

    // ---- PHYSICAL fields (required when classType = PHYSICAL) ----

    @Size(max = 300)
    private String classLocation;

    // ---- Common ----

    private String notes;

    // THIS_ONLY / THIS_AND_FUTURE / ALL
    private String editScope = "THIS_ONLY";

    private boolean overrideConflict = false;
}