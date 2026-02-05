package com.reportserver.controller;

import com.reportserver.model.User;
import com.reportserver.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "logout", required = false) String logout,
                       Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }
    
    @PostMapping("/register")
    public String registerUser(@RequestParam("username") String username,
                              @RequestParam("email") String email,
                              @RequestParam("password") String password,
                              @RequestParam("confirmPassword") String confirmPassword,
                              RedirectAttributes redirectAttributes) {
        try {
            // Validate input
            if (username == null || username.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Username is required");
                return "redirect:/register";
            }
            
            if (email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Email is required");
                return "redirect:/register";
            }
            
            if (password == null || password.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Password is required");
                return "redirect:/register";
            }
            
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                return "redirect:/register";
            }
            
            if (password.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long");
                return "redirect:/register";
            }
            
            // Register user
            userService.registerUser(username.trim(), email.trim(), password);
            
            redirectAttributes.addFlashAttribute("message", "Registration successful! Please log in.");
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            logger.error("Registration failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        } catch (Exception e) {
            logger.error("Registration failed with unexpected error", e);
            redirectAttributes.addFlashAttribute("error", "Registration failed. Please try again.");
            return "redirect:/register";
        }
    }
    
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }
    
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email,
                                       RedirectAttributes redirectAttributes) {
        try {
            if (email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Email is required");
                return "redirect:/forgot-password";
            }
            
            String token = userService.createPasswordResetToken(email.trim());
            
            // In a real application, you would send this token via email
            // For now, we'll display it (in production, use email service)
            logger.info("Password reset token for {}: {}", email, token);
            
            redirectAttributes.addFlashAttribute("message", 
                "Password reset instructions have been sent to your email. " +
                "Token: " + token + " (In production, this would be emailed)");
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            logger.error("Password reset failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/forgot-password";
        } catch (Exception e) {
            logger.error("Password reset failed with unexpected error", e);
            redirectAttributes.addFlashAttribute("error", "Failed to process password reset. Please try again.");
            return "redirect:/forgot-password";
        }
    }
    
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        if (!userService.validateResetToken(token)) {
            model.addAttribute("error", "Invalid or expired reset token");
            return "reset-password";
        }
        
        model.addAttribute("token", token);
        return "reset-password";
    }
    
    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                      @RequestParam("password") String password,
                                      @RequestParam("confirmPassword") String confirmPassword,
                                      RedirectAttributes redirectAttributes) {
        try {
            if (password == null || password.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Password is required");
                redirectAttributes.addAttribute("token", token);
                return "redirect:/reset-password";
            }
            
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                redirectAttributes.addAttribute("token", token);
                return "redirect:/reset-password";
            }
            
            if (password.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long");
                redirectAttributes.addAttribute("token", token);
                return "redirect:/reset-password";
            }
            
            userService.resetPassword(token, password);
            
            redirectAttributes.addFlashAttribute("message", "Password reset successful! Please log in with your new password.");
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            logger.error("Password reset failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("token", token);
            return "redirect:/reset-password";
        } catch (Exception e) {
            logger.error("Password reset failed with unexpected error", e);
            redirectAttributes.addFlashAttribute("error", "Failed to reset password. Please try again.");
            redirectAttributes.addAttribute("token", token);
            return "redirect:/reset-password";
        }
    }
}
