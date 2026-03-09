package com.reportserver.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JWT Authentication Filter for REST API endpoints.
 * Validates JWT tokens in Authorization header (Bearer <token>).
 * If valid, creates an authentication token in SecurityContext.
 * If invalid or missing, allows Spring Security to handle the request normally.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractJwtToken(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                
                if (username != null) {
                    // Extract authorities from JWT claims
                    Collection<? extends GrantedAuthority> authorities = extractAuthorities(token);
                    
                    // Create authentication token
                    AbstractAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("JWT authentication successful for user: {}", username);
                }
            }
        } catch (Exception e) {
            logger.error("JWT authentication filter error", e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractJwtToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
    
    /**
     * Extract authorities from JWT token claims
     */
    private Collection<? extends GrantedAuthority> extractAuthorities(String token) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<String> authList = (List<String>) jwtTokenProvider.getClaimsFromToken(token)
                .get("authorities", List.class);
            
            if (authList != null) {
                authList.forEach(auth -> authorities.add(new SimpleGrantedAuthority(auth)));
            }
        } catch (Exception e) {
            logger.debug("Failed to extract authorities from token", e);
        }
        
        return authorities;
    }
    
    /**
     * Skip JWT filter for certain paths (login, register, public endpoints)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/login") || 
               path.startsWith("/register") ||
               path.startsWith("/forgot-password") ||
               path.startsWith("/reset-password") ||
               path.startsWith("/css") ||
               path.startsWith("/js") ||
               path.startsWith("/images") ||
               path.startsWith("/error");
    }
}
