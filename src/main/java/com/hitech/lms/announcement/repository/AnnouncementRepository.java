package com.hitech.lms.announcement.repository;
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


import com.hitech.lms.announcement.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** System-wide (general) active announcements, newest first. */
    @Query("SELECT a FROM Announcement a WHERE a.type = 'SYSTEM' AND a.isActive = true ORDER BY a.createdAt DESC")
    List<Announcement> findActiveSystemAnnouncements();

    /** Active announcements for a specific course, newest first. */
    @Query("SELECT a FROM Announcement a WHERE a.course.id = :courseId AND a.isActive = true ORDER BY a.createdAt DESC")
    List<Announcement> findActiveByCourseId(@Param("courseId") Long courseId);

    /** All active announcements for a list of course IDs (for student feed). */
    @Query("SELECT a FROM Announcement a WHERE a.course.id IN :courseIds AND a.isActive = true ORDER BY a.createdAt DESC")
    List<Announcement> findActiveByCourseIds(@Param("courseIds") List<Long> courseIds);

    /** All announcements posted by a specific author (for Instructor management view). */
    @Query("SELECT a FROM Announcement a WHERE a.author.id = :authorId AND a.isActive = true ORDER BY a.createdAt DESC")
    List<Announcement> findActiveByAuthorId(@Param("authorId") Long authorId);

    /** All active announcements for Admin management view (all types). */
    @Query("SELECT a FROM Announcement a WHERE a.isActive = true ORDER BY a.createdAt DESC")
    List<Announcement> findAllActive();
}
