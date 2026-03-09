package com.reportserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token generator and validator for API authentication.
 * Allows programmatic API access without session cookies.
 */
@Service
public class JwtTokenProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    
    @Value("${reportserver.jwt.secret:dev-secret-key-change-in-production}")
    private String jwtSecret;
    
    @Value("${reportserver.jwt.expiration:3600000}")
    private long jwtExpirationMs;
    
    private SecretKey key;
    
    /**
     * Get or create the signing key
     */
    private synchronized SecretKey getSigningKey() {
        if (key == null) {
            // Ensure secret is at least 32 bytes for HS256
            String secret = jwtSecret.length() < 32 
                ? jwtSecret + "0".repeat(Math.max(0, 32 - jwtSecret.length()))
                : jwtSecret;
            key = Keys.hmacShaKeyFor(secret.getBytes());
        }
        return key;
    }
    
    /**
     * Generate JWT token for a user
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        claims.put("authorities", userDetails.getAuthorities()
            .stream()
            .map(auth -> auth.getAuthority())
            .toList());
        
        return createToken(claims, userDetails.getUsername());
    }
    
    /**
     * Generate JWT token with custom claims (for API keys, etc)
     */
    public String generateToken(String username, Map<String, Object> claims) {
        claims.putIfAbsent("username", username);
        return createToken(claims, username);
    }
    
    /**
     * Create the actual JWT token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }
    
    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            logger.error("JWT validation failed", e);
            return false;
        }
    }
    
    /**
     * Extract username from JWT token
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Failed to extract username from token", e);
            return null;
        }
    }
    
    /**
     * Extract claims from JWT token
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            logger.error("Failed to extract claims from token", e);
            return null;
        }
    }
}
