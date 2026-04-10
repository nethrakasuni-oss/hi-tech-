package com.hitech.lms.security;
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


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig — The central security configuration for the LMS.
 *
 * This class answers three questions:
 * 1. Which routes are public (no login required)?
 * 2. Which routes require a specific role (Admin, Instructor, Student)?
 * 3. How is authentication managed (JWT, stateless)?
 *
 * FR-1.2: RBAC — enforced here at the API level (security boundary)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize on controller methods
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * The main security filter chain — defines all security rules.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF — not needed for stateless REST APIs with JWT
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure CORS — allow the frontend (running on port 5500) to call our API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Stateless session — no server-side session storage; JWT handles state
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Define which endpoints are public and which require authentication
                .authorizeHttpRequests(auth -> auth

                        // ---- PUBLIC ROUTES (no token needed) ----
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/refresh-token"
                        ).permitAll()

                        // ---- ADMIN-ONLY ROUTES ----
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ---- COURSE ROUTES (Admin + Instructor manage; all authenticated can read) ----
                        // These are further controlled at the method level with @PreAuthorize
                        .requestMatchers("/api/courses/**").authenticated()

                        .requestMatchers("/api/payments/**").authenticated()
                        .requestMatchers("/api/admin/finance/**").authenticated()

                        .requestMatchers("/api/schedule/**").authenticated()

                        .requestMatchers("/api/exams/**").authenticated()
                        .requestMatchers("/api/exam-attempts/**").authenticated()

                        .requestMatchers("/api/support/**").authenticated()
                        .requestMatchers("/api/appointments/**").authenticated()
                        .requestMatchers("/api/knowledge-base/**").authenticated()

                        .requestMatchers("/api/announcements/**").authenticated()

                        // ---- AUTHENTICATED ROUTES (any valid logged-in user) ----
                        .requestMatchers("/api/users/profile/**").authenticated()
                        .requestMatchers("/api/users/me").authenticated()

                        // ---- ALL OTHER ROUTES require authentication ----
                        .anyRequest().authenticated()
                )

                // 5. Add our JWT filter BEFORE Spring's default username/password filter
                // This means: check JWT first, then proceed
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder using BCrypt with cost factor 12.
     * FR-1.1: "Password is hashed with bcrypt (cost factor ≥ 12)"
     * BCrypt automatically salts the password — no duplicate hashes even for same passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AuthenticationManager — used by the login service to validate credentials.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS Configuration — Cross-Origin Resource Sharing.
     * This allows the frontend (HTML files opened from VS Code / Live Server on port 5500)
     * to make API calls to the backend (running on port 8080).
     * Without this, the browser would block all API calls from the frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from the frontend URL
        config.setAllowedOrigins(List.of(
                "http://localhost:5500",    // VS Code Live Server
                "http://127.0.0.1:5500",   // Alternative Live Server URL
                "http://localhost:3000",    // In case they use npm serve
                "http://localhost:8080"     // Same-origin (for testing via browser)
        ));

        // Allow all standard HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers (including Authorization for JWT)
        config.setAllowedHeaders(Arrays.asList("*"));

        // Allow the Authorization header to be read by the frontend
        config.setExposedHeaders(List.of("Authorization"));

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS requests)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // Apply to all routes
        return source;
    }
}