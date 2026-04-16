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
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JwtUtils — Creates and validates JWT tokens.
 *
 * JWT = JSON Web Token. It's a secure string the server gives to the user
 * after login. The user sends it back with every request to prove who they are.
 *
 * Structure: header.payload.signature
 * Payload contains: user_id, email, role, issued_at, expiry
 */
@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    // Read the secret key from application.properties
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Read access token expiry from application.properties (default 1 hour)
    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    /**
     * Creates a signed JWT access token for a logged-in user.
     * The token contains: userId, email, role (as claims).
     */
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())               // "sub" claim: user ID
                .claim("email", user.getEmail())                // custom claim: email
                .claim("role", user.getRole().name())           // custom claim: role
                .claim("fullName", user.getFullName())          // custom claim: name
                .issuedAt(new Date())                           // "iat" claim: issued now
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // "exp"
                .signWith(getSigningKey())                       // sign with secret key
                .compact();                                     // build the final string
    }

    /**
     * Extracts the user ID from a JWT token.
     * The user ID is stored in the "sub" (subject) claim.
     */
    public Long getUserIdFromToken(String token) {
        String subject = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.parseLong(subject);
    }

    /**
     * Extracts the role from a JWT token.
     */
    public String getRoleFromToken(String token) {
        return (String) Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role");
    }

    /**
     * Validates a JWT token. Returns true if:
     * - Signature is valid (not tampered)
     * - Token is not expired
     * - Token is well-formed
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Converts the secret string from properties into a cryptographic key.
     * Uses HMAC-SHA256 algorithm.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}