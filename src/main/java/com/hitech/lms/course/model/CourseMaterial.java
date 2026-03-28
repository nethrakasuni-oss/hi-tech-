package com.hitech.lms.course.model;
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


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * CourseMaterial Entity — maps to 'course_materials' table.
 *
 * FR-2.2: Instructors upload PDF, PPTX files or add Video/URL links.
 * Each material is attached to a ContentNode (Week/Module/Topic).
 * Materials have a version number — each new upload increments the version.
 */
@Entity
@Table(name = "course_materials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which course this material belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Which hierarchy node this material is attached to (nullable = uncategorized)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    private ContentNode node;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // PDF, PPTX, VIDEO_LINK, EXTERNAL_URL
    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false)
    private MaterialType materialType;

    // For files: object storage URL. For links: the external URL.
    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    // File size in bytes (null for links)
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // Version tracking — starts at 1, increments on each re-upload
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    // Position within the node
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    // Who uploaded this material
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ---- Enum ----
    public enum MaterialType {
        PDF,
        PPTX,
        VIDEO_LINK,
        EXTERNAL_URL
    }

    // ---- Helper ----
    /** Returns a human-readable file size string */
    public String getFormattedFileSize() {
        if (fileSizeBytes == null) return null;
        if (fileSizeBytes < 1024) return fileSizeBytes + " B";
        if (fileSizeBytes < 1048576) return String.format("%.1f KB", fileSizeBytes / 1024.0);
        return String.format("%.1f MB", fileSizeBytes / 1048576.0);
    }
}