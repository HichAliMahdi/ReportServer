package com.reportserver.service;

import com.reportserver.model.User;
import com.reportserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
            .authorities(getAuthoritiesForRole(user.getRole()))
                .build();
    }
    
    public User registerUser(String username, String email, String password) {
        // Validate inputs
        validateBasicInputs(username, email, password);
        
        // Trim username and email
        username = username.trim();
        email = email.trim();
        
        // Validate email format
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        // Validate password strength
        validatePasswordStrength(password);
        
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        
        logger.info("Registering new user: {}", username);
        return userRepository.save(user);
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email not found"));
        
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1)); // Token expires in 1 hour
        
        userRepository.save(user);
        logger.info("Password reset token created for user: {}", user.getUsername());
        
        return token;
    }
    
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));
        
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }
        
        // Validate new password strength
        validatePasswordStrength(newPassword);
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        
        userRepository.save(user);
        logger.info("Password reset successful for user: {}", user.getUsername());
    }
    
    public boolean validateResetToken(String token) {
        Optional<User> userOpt = userRepository.findByResetToken(token);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        return user.getResetTokenExpiry() != null && user.getResetTokenExpiry().isAfter(LocalDateTime.now());
    }
    
    // User Management Methods
    
    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    public User createUser(String username, String email, String password, String role) {
        // Validate inputs
        validateBasicInputs(username, email, password);
        
        // Trim username and email
        username = username.trim();
        email = email.trim();
        role = normalizeRole(role != null ? role.trim() : "READ_ONLY");
        
        // Validate email format
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        // Validate role
        validateRole(role);
        
        // Validate password strength
        validatePasswordStrength(password);
        
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        user.setFirstLogin(true);
        
        logger.info("Creating new user: {} with role: {}", username, role);
        return userRepository.save(user);
    }
    
    public User updateUser(Long id, String email, String role, Boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (email != null && !email.equals(user.getEmail())) {
            // Validate email format
            if (!isValidEmail(email)) {
                throw new IllegalArgumentException("Invalid email format");
            }
            
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(email);
        }
        
        if (role != null) {
            // Validate role
            String normalizedRole = normalizeRole(role);
            validateRole(normalizedRole);
            user.setRole(normalizedRole);
        }
        
        if (enabled != null) {
            user.setEnabled(enabled);
        }
        
        logger.info("Updating user: {}", user.getUsername());
        return userRepository.save(user);
    }
    
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        logger.info("Deleting user: {}", user.getUsername());
        userRepository.delete(user);
    }
    
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new IllegalArgumentException("Current password is required");
        }
        
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password is required");
        }
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        // Validate new password strength
        validatePasswordStrength(newPassword);
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        
        userRepository.save(user);
        logger.info("Password changed for user: {}", username);
    }
    
    // Helper method to validate email format
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Basic email validation regex - allows TLDs of 2 or more characters
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
    
    // Helper method to validate password strength
    private void validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        // Optional: Add more password complexity requirements
        // For now, we'll just enforce minimum length
    }
    
    // Helper method to validate role
    private void validateRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role is required");
        }
        
        if (!role.equals("ADMIN") && !role.equals("OPERATOR") && !role.equals("READ_ONLY")) {
            throw new IllegalArgumentException("Invalid role. Must be ADMIN, OPERATOR, or READ_ONLY");
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "READ_ONLY";
        }
        if ("USER".equals(role)) {
            return "READ_ONLY";
        }
        return role;
    }

    private String[] getAuthoritiesForRole(String role) {
        String normalizedRole = normalizeRole(role);
        if ("READ_ONLY".equals(normalizedRole)) {
            return new String[]{"ROLE_READ_ONLY", "ROLE_USER"};
        }
        return new String[]{"ROLE_" + normalizedRole};
    }
    
    // Helper method to validate basic input fields
    private void validateBasicInputs(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
    
    public long countUsers() {
        return userRepository.count();
    }
}
