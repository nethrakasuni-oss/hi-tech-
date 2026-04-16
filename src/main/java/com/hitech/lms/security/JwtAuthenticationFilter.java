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


import com.hitech.lms.auth.model.User;
import com.hitech.lms.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * JwtAuthenticationFilter
 *
 * This filter runs ONCE for every HTTP request that comes into the server.
 * It checks the "Authorization" header for a JWT token.
 * If the token is valid, it tells Spring Security who the user is.
 * If there's no token or it's invalid, the request continues as anonymous.
 *
 * Flow: Request → This Filter → (if valid token) → Set authentication → Controller
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Step 1: Extract the JWT from the Authorization header
            // Header format: "Authorization: Bearer eyJhbGci..."
            String jwt = parseJwt(request);

            // Step 2: If a token exists and it's valid, authenticate the user
            if (jwt != null && jwtUtils.validateToken(jwt)) {

                // Step 3: Extract user ID from the token
                Long userId = jwtUtils.getUserIdFromToken(jwt);

                // Step 4: Load the user from the database
                Optional<User> userOptional = userRepository.findById(userId);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();

                    // Step 5: Check if user account is still ACTIVE
                    // If account was deactivated, reject the request with 401
                    if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"error\": \"Your account has been deactivated. Please contact the administrator.\"}"
                        );
                        return;
                    }

                    // Step 6: Create the Spring Security authentication object
                    // The role is prefixed with "ROLE_" as required by Spring Security
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                            "ROLE_" + user.getRole().name()
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,           // principal (the logged-in user object)
                                    null,           // credentials (not needed after login)
                                    List.of(authority)  // authorities (permissions)
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Step 7: Tell Spring Security this request is authenticated
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Step 8: Continue processing the request (goes to the controller)
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the Authorization header.
     * Returns null if no valid Bearer token is found.
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        // Check that the header starts with "Bearer "
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Remove "Bearer " prefix
        }

        return null;
    }
}