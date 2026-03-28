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


import com.hitech.lms.course.model.CourseMaterial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/courses/{id}/materials — Add a material to a course node
 * FR-2.2
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class AddMaterialRequest {

    @NotBlank(message = "Material title is required")
    @Size(max = 200)
    private String title;

    private String description;

    @NotNull(message = "Material type is required")
    private CourseMaterial.MaterialType materialType; // PDF, PPTX, VIDEO_LINK, EXTERNAL_URL

    @NotBlank(message = "File URL is required")
    @Size(max = 1000)
    private String fileUrl;  // S3 URL for files, external URL for links

    private Long fileSizeBytes;

    // Which content node to attach to (null = uncategorized)
    private Long nodeId;

    private Integer sortOrder;
}
