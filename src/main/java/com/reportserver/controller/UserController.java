package com.reportserver.controller;

import com.reportserver.dto.UserCreationDTO;
import com.reportserver.model.User;
import com.reportserver.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class UserController {
    
    @Autowired
    private UserService userService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;
    
    // Page for user management (Admin only)
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String usersPage(Model model) {
        return "user-management";
    }
    
    // Page for changing password (All authenticated users)
    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userService.findByUsername(username).orElse(null);
        
        if (user != null && user.isFirstLogin()) {
            model.addAttribute("firstLogin", true);
        }
        
        return "change-password";
    }
    
    // API: Get all users (Admin only)
    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(resolvedSize, 1));
        Page<User> users = userService.getUsersPage(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", users.getContent());
        response.put("page", users.getNumber());
        response.put("size", users.getSize());
        response.put("totalElements", users.getTotalElements());
        response.put("totalPages", users.getTotalPages());
        response.put("first", users.isFirst());
        response.put("last", users.isLast());
        return ResponseEntity.ok(response);
    }
    
    // API: Get user by ID (Admin only)
    @GetMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    // API: Create user (Admin only)
    @PostMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody UserCreationDTO userDTO, BindingResult bindingResult) {
        Map<String, Object> response = new HashMap<>();
        
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            response.put("status", "error");
            response.put("message", errors);
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            User user = userService.createUser(
                userDTO.getUsername(), 
                userDTO.getEmail(), 
                userDTO.getPassword(), 
                userDTO.getRole()
            );
            
            response.put("status", "success");
            response.put("message", "User created successfully");
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // API: Update user (Admin only)
    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            User currentUser = userService.findByUsername(currentUsername).orElse(null);
            
            // Prevent admin from demoting or disabling themselves
            if (currentUser != null && currentUser.getId().equals(id)) {
                String newRole = (String) request.get("role");
                Boolean enabled = (Boolean) request.get("enabled");
                
                if ((newRole != null && !"ADMIN".equals(newRole)) || (enabled != null && !enabled)) {
                    response.put("status", "error");
                    response.put("message", "You cannot demote or disable your own account");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            String email = (String) request.get("email");
            String role = (String) request.get("role");
            Boolean enabled = (Boolean) request.get("enabled");
            
            User user = userService.updateUser(id, email, role, enabled);
            
            response.put("status", "success");
            response.put("message", "User updated successfully");
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // API: Delete user (Admin only)
    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            User currentUser = userService.findByUsername(currentUsername).orElse(null);
            
            // Prevent admin from deleting themselves
            if (currentUser != null && currentUser.getId().equals(id)) {
                response.put("status", "error");
                response.put("message", "You cannot delete your own account");
                return ResponseEntity.badRequest().body(response);
            }
            
            userService.deleteUser(id);
            
            response.put("status", "success");
            response.put("message", "User deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // API: Reset user password (Admin only)
    @PostMapping("/api/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetUserPassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String newPassword = request.get("newPassword");
            
            if (newPassword == null || newPassword.isEmpty()) {
                response.put("status", "error");
                response.put("message", "New password is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            User currentUser = userService.findByUsername(currentUsername).orElse(null);
            
            // Prevent admin from resetting their own password through this endpoint
            if (currentUser != null && currentUser.getId().equals(id)) {
                response.put("status", "error");
                response.put("message", "Use the change password feature to update your own password");
                return ResponseEntity.badRequest().body(response);
            }
            
            userService.resetPassword(id, newPassword);
            
            response.put("status", "success");
            response.put("message", "Password reset successfully. User must set a new password on next login");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // API: Get current user info (All authenticated users)
    @GetMapping("/api/current-user")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            User user = userService.findByUsername(username).orElse(null);
            
            if (user == null) {
                response.put("status", "error");
                response.put("message", "User not found");
                return ResponseEntity.status(404).body(response);
            }
            
            response.put("status", "success");
            response.put("username", user.getUsername());
            response.put("role", user.getRole());
            response.put("email", user.getEmail());
            response.put("enabled", user.isEnabled());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // API: Change password (All authenticated users)
    @PostMapping("/api/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            
            userService.changePassword(username, oldPassword, newPassword);
            
            response.put("status", "success");
            response.put("message", "Password changed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
