package com.reportserver.controller;

import com.reportserver.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API Authentication Controller - Issues JWT tokens for programmatic API access
 * 
 * Usage:
 * 1. POST /api/auth/login with {"username": "user", "password": "pass"} to get a token
 * 2. Use the token in subsequent requests: Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiAuthController.class);
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    /**
     * Login endpoint - returns JWT token for API access
     * 
     * Request: {"username": "admin", "password": "password"}
     * Response: {"success": true, "token": "eyJhbGc...", "username": "admin", "expiresIn": 3600000}
     */
    @PostMapping("/login")
    @CrossOrigin(origins = "*")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            if (username == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Username and password are required"));
            }
            
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Get user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            // Generate JWT token
            String token = jwtTokenProvider.generateToken(userDetails);
            
            response.put("success", true);
            response.put("token", token);
            response.put("username", username);
            response.put("expiresIn", 3600000); // 1 hour in milliseconds
            
            logger.info("JWT token issued for user: {}", username);
            return ResponseEntity.ok(response);
            
        } catch (AuthenticationException e) {
            logger.warn("Authentication failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (Exception e) {
            logger.error("Error during login", e);
            response.put("success", false);
            response.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Validate JWT token - check if a token is still valid
     * 
     * Request: Authorization header with Bearer token OR body with {"token": "..."}
     * Response: {"valid": true, "username": "admin", "message": "Token is valid"}
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String jwtToken = null;
            
            // Extract token from Authorization header
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtToken = authHeader.substring(7);
            } else if (token != null) {
                jwtToken = token;
            }
            
            if (jwtToken == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "message", "No token provided"));
            }
            
            boolean isValid = jwtTokenProvider.validateToken(jwtToken);
            
            if (isValid) {
                String username = jwtTokenProvider.getUsernameFromToken(jwtToken);
                response.put("valid", true);
                response.put("username", username);
                response.put("message", "Token is valid");
                return ResponseEntity.ok(response);
            } else {
                response.put("valid", false);
                response.put("message", "Token is invalid or expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error validating token", e);
            response.put("valid", false);
            response.put("message", "Token validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get current user info from JWT token in SecurityContext
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                Map<String, Object> user = new HashMap<>();
                user.put("username", authentication.getName());
                user.put("authorities", authentication.getAuthorities()
                    .stream()
                    .map(auth -> auth.getAuthority())
                    .toList());
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }
        } catch (Exception e) {
            logger.error("Error getting current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error: " + e.getMessage()));
        }
    }
}
