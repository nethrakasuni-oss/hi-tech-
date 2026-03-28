package com.hitech.lms.course.service;
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


import com.hitech.lms.dto.ApiResponse;
import com.hitech.lms.auth.dto.*;
import com.hitech.lms.user.dto.*;
import com.hitech.lms.course.dto.*;
import com.hitech.lms.exam.dto.*;
import com.hitech.lms.schedule.dto.*;
import com.hitech.lms.finance.dto.*;
import com.hitech.lms.support.dto.*;
import com.hitech.lms.auth.model.*;
import com.hitech.lms.course.model.*;
import com.hitech.lms.exam.model.*;
import com.hitech.lms.schedule.model.*;
import com.hitech.lms.finance.model.*;
import com.hitech.lms.support.model.*;
import com.hitech.lms.auth.repository.*;
import com.hitech.lms.course.repository.*;
import com.hitech.lms.exam.repository.*;
import com.hitech.lms.schedule.repository.*;
import com.hitech.lms.finance.repository.*;
import com.hitech.lms.support.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ContentService — Business logic for FR-2.2 (Content Upload & Organisation).
 *
 * Manages the Week → Module → Topic hierarchy tree and
 * the CourseMaterials (PDF, PPTX, video links, external URLs) attached to each node.
 */
@Service
@Transactional
public class ContentService {

    private static final Logger logger = LoggerFactory.getLogger(ContentService.class);

    @Autowired private ContentNodeRepository    contentNodeRepository;
    @Autowired private CourseMaterialRepository materialRepository;
    @Autowired private CourseService            courseService;

    // =====================================================
    // GET FULL CONTENT TREE FOR A COURSE
    // Returns a recursive tree: WEEK → MODULE → TOPIC
    // =====================================================

    @Transactional(readOnly = true)
    public List<ContentNodeResponse> getContentTree(Long courseId) {
        // Verify the course exists
        courseService.findCourseById(courseId);

        // Load all WEEK (root) nodes, each with its children loaded lazily
        List<ContentNode> weeks = contentNodeRepository
            .findByCourseIdAndParentIsNullOrderBySortOrderAsc(courseId);

        return weeks.stream()
            .map(w -> mapNodeToResponse(w, true))
            .collect(Collectors.toList());
    }

    // =====================================================
    // CREATE CONTENT NODE (Week / Module / Topic)
    // FR-2.2: Instructors build the Week → Module → Topic tree
    // =====================================================

    public ContentNodeResponse createNode(Long courseId, CreateContentNodeRequest request, User creator) {
        Course course = courseService.findCourseById(courseId);
        validateContentAccess(course, creator);

        // Validate parent rules
        ContentNode parent = null;
        if (request.getParentId() != null) {
            parent = contentNodeRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Parent node not found."));

            // Ensure parent belongs to the same course
            if (!parent.getCourse().getId().equals(courseId)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Parent node does not belong to this course.");
            }

            // WEEK must have no parent; MODULE must have WEEK parent; TOPIC must have MODULE parent
            validateNodeHierarchy(request.getNodeType(), parent.getNodeType());
        } else {
            if (request.getNodeType() != ContentNode.NodeType.WEEK) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only WEEK nodes can be at the top level (no parent).");
            }
        }

        // Calculate sort order if not provided
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder()
            : calculateNextSortOrder(courseId, request.getParentId());

        ContentNode node = ContentNode.builder()
            .course(course)
            .parent(parent)
            .nodeType(request.getNodeType())
            .title(request.getTitle().trim())
            .sortOrder(sortOrder)
            .build();

        node = contentNodeRepository.save(node);
        logger.info("Content node created: {} ({}) in course {}", node.getTitle(), node.getNodeType(), courseId);

        return mapNodeToResponse(node, false);
    }

    // =====================================================
    // UPDATE CONTENT NODE (rename / reorder)
    // =====================================================

    public ContentNodeResponse updateNode(Long nodeId, String newTitle, Integer newSortOrder, User updater) {
        ContentNode node = findNodeById(nodeId);
        validateContentAccess(node.getCourse(), updater);

        if (newTitle != null && !newTitle.isBlank()) {
            node.setTitle(newTitle.trim());
        }
        if (newSortOrder != null) {
            node.setSortOrder(newSortOrder);
        }

        contentNodeRepository.save(node);
        return mapNodeToResponse(node, false);
    }

    // =====================================================
    // DELETE CONTENT NODE
    // Cascades: deletes all children and their materials
    // =====================================================

    public void deleteNode(Long nodeId, User deleter) {
        ContentNode node = findNodeById(nodeId);
        validateContentAccess(node.getCourse(), deleter);
        contentNodeRepository.delete(node);
        logger.info("Content node deleted: {} by {}", nodeId, deleter.getEmail());
    }

    // =====================================================
    // ADD MATERIAL TO A COURSE NODE
    // FR-2.2: PDF, PPTX files or VIDEO_LINK / EXTERNAL_URL
    // =====================================================

    public MaterialResponse addMaterial(Long courseId, AddMaterialRequest request, User uploader) {
        Course course = courseService.findCourseById(courseId);
        validateContentAccess(course, uploader);

        // Validate URL format for links
        if ((request.getMaterialType() == CourseMaterial.MaterialType.VIDEO_LINK
                || request.getMaterialType() == CourseMaterial.MaterialType.EXTERNAL_URL)
                && !request.getFileUrl().startsWith("https://")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "External links must use HTTPS (e.g. https://youtube.com/...).");
        }

        // Resolve node (may be null for uncategorized)
        ContentNode node = null;
        if (request.getNodeId() != null) {
            node = findNodeById(request.getNodeId());
            if (!node.getCourse().getId().equals(courseId)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Node does not belong to this course.");
            }
        }

        int sortOrder = request.getSortOrder() != null ? request.getSortOrder()
            : (int) materialRepository.countByCourseId(courseId);

        CourseMaterial material = CourseMaterial.builder()
            .course(course)
            .node(node)
            .title(request.getTitle().trim())
            .description(request.getDescription())
            .materialType(request.getMaterialType())
            .fileUrl(request.getFileUrl())
            .fileSizeBytes(request.getFileSizeBytes())
            .version(1)
            .sortOrder(sortOrder)
            .uploadedBy(uploader)
            .build();

        material = materialRepository.save(material);
        logger.info("Material added: '{}' to course {} by {}", material.getTitle(), courseId, uploader.getEmail());

        return mapMaterialToResponse(material);
    }

    // =====================================================
    // DELETE MATERIAL
    // =====================================================

    public void deleteMaterial(Long materialId, User deleter) {
        CourseMaterial material = materialRepository.findById(materialId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Material not found."));

        validateContentAccess(material.getCourse(), deleter);
        materialRepository.delete(material);
        logger.info("Material deleted: {} by {}", materialId, deleter.getEmail());
    }

    // =====================================================
    // GET MATERIALS FOR A SPECIFIC NODE
    // =====================================================

    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterialsForNode(Long nodeId) {
        return materialRepository.findByNodeIdOrderBySortOrderAsc(nodeId)
            .stream()
            .map(this::mapMaterialToResponse)
            .collect(Collectors.toList());
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private ContentNode findNodeById(Long nodeId) {
        return contentNodeRepository.findById(nodeId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Content node not found."));
    }

    /**
     * Validates that the user has permission to modify content for this course.
     * Admin can modify any course.
     * Instructor can only modify their assigned courses.
     * Students cannot modify content.
     */
    private void validateContentAccess(Course course, User user) {
        if (user.getRole() == User.Role.ADMIN) return;

        if (user.getRole() == User.Role.STUDENT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Students cannot modify course content.");
        }

        // Instructor: must be assigned to the course
        boolean isAssigned = course.getInstructors().stream()
            .anyMatch(i -> i.getId().equals(user.getId()));
        if (!isAssigned) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You are not assigned to this course.");
        }
    }

    /**
     * Validates that the node type hierarchy is correct:
     * MODULE can only be a child of WEEK
     * TOPIC can only be a child of MODULE
     */
    private void validateNodeHierarchy(ContentNode.NodeType childType, ContentNode.NodeType parentType) {
        if (childType == ContentNode.NodeType.MODULE && parentType != ContentNode.NodeType.WEEK) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A MODULE must be placed inside a WEEK.");
        }
        if (childType == ContentNode.NodeType.TOPIC && parentType != ContentNode.NodeType.MODULE) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A TOPIC must be placed inside a MODULE.");
        }
        if (childType == ContentNode.NodeType.WEEK) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A WEEK cannot be placed inside another node.");
        }
    }

    private int calculateNextSortOrder(Long courseId, Long parentId) {
        List<ContentNode> siblings = (parentId == null)
            ? contentNodeRepository.findByCourseIdAndParentIsNullOrderBySortOrderAsc(courseId)
            : contentNodeRepository.findByParentIdOrderBySortOrderAsc(parentId);
        return siblings.size();
    }

    private ContentNodeResponse mapNodeToResponse(ContentNode node, boolean includeChildren) {
        List<MaterialResponse> materials = materialRepository
            .findByNodeIdOrderBySortOrderAsc(node.getId())
            .stream()
            .map(this::mapMaterialToResponse)
            .collect(Collectors.toList());

        List<ContentNodeResponse> children = null;
        if (includeChildren) {
            children = contentNodeRepository.findByParentIdOrderBySortOrderAsc(node.getId())
                .stream()
                .map(child -> mapNodeToResponse(child, true))
                .collect(Collectors.toList());
        }

        return ContentNodeResponse.builder()
            .id(node.getId())
            .nodeType(node.getNodeType().name())
            .title(node.getTitle())
            .parentId(node.getParent() != null ? node.getParent().getId() : null)
            .sortOrder(node.getSortOrder())
            .createdAt(node.getCreatedAt())
            .children(children)
            .materials(materials)
            .build();
    }

    private MaterialResponse mapMaterialToResponse(CourseMaterial m) {
        return MaterialResponse.builder()
            .id(m.getId())
            .title(m.getTitle())
            .description(m.getDescription())
            .materialType(m.getMaterialType().name())
            .fileUrl(m.getFileUrl())
            .formattedFileSize(m.getFormattedFileSize())
            .version(m.getVersion())
            .nodeId(m.getNode() != null ? m.getNode().getId() : null)
            .uploadedByName(m.getUploadedBy().getFullName())
            .createdAt(m.getCreatedAt())
            .build();
    }
}
