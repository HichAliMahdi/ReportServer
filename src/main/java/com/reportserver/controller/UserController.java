package com.reportserver.controller;

import com.reportserver.model.User;
import com.reportserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class UserController {
    
    @Autowired
    private UserService userService;
    
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
    public List<User> getAllUsers() {
        return userService.getAllUsers();
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
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = request.get("username");
            String email = request.get("email");
            String password = request.get("password");
            String role = request.getOrDefault("role", "USER");
            
            User user = userService.createUser(username, email, password, role);
            
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
