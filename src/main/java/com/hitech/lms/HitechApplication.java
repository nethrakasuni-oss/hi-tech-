package com.hitech.lms;
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


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LmsApplication — The entry point of the Hi-Tech Institute LMS.
 *
 * @SpringBootApplication includes:
 *   - @Configuration       : marks this as a config class
 *   - @EnableAutoConfiguration : auto-configures Spring beans
 *   - @ComponentScan       : scans com.hitech.lms for all @Component, @Service, etc.
 *
 * @EnableAsync  : allows @Async methods (used in EmailService for background email sending)
 * @EnableScheduling : allows @Scheduled tasks (e.g. cleanup of expired tokens)
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HitechApplication {

    public static void main(String[] args) {
        SpringApplication.run(HitechApplication.class, args);
        System.out.println("\n==========================================");
        System.out.println("  Hi-Tech Institute LMS is running!");
        System.out.println("  API Base URL: http://localhost:8080");
        System.out.println("  Default Admin: admin@hitech.com");
        System.out.println("  Default Password: Admin2000.");
        System.out.println("==========================================\n");
    }
}