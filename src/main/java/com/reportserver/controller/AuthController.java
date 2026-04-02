package com.reportserver.controller;

import com.reportserver.model.User;
import com.reportserver.service.EmailNotificationService;
import com.reportserver.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private UserService userService;

    private EmailNotificationService emailNotificationService;

    public AuthController(UserService userService, EmailNotificationService emailNotificationService) {
        this.userService = userService;
        this.emailNotificationService = emailNotificationService;
    }
    
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "logout", required = false) String logout,
                       Model model) {
        if (error != null) {
            if ("rate_limit".equals(error)) {
                model.addAttribute("error", "Too many login attempts. Please wait before retrying.");
            } else {
                model.addAttribute("error", "Invalid username or password");
            }
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
            
            if (password.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters long");
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
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        try {
            if (email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Email is required");
                return "redirect:/forgot-password";
            }
            
            String token = userService.createPasswordResetToken(email.trim());
            String baseUrl = resolvePublicBaseUrl(request);

            userService.findByEmail(email.trim()).ifPresent(user -> {
                try {
                    emailNotificationService.sendPasswordResetLink(user, token, baseUrl);
                } catch (Exception ex) {
                    logger.error("Failed to send password reset email to {}", user.getEmail(), ex);
                }
            });
            
            redirectAttributes.addFlashAttribute("message", 
                "If an account with that email exists, password reset instructions have been sent.");
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            // Don't reveal whether email exists - use generic message
            logger.debug("Password reset request for non-existent email: {}", email);
            redirectAttributes.addFlashAttribute("message", 
                "If an account with that email exists, password reset instructions have been sent.");
            return "redirect:/login";
        } catch (Exception e) {
            logger.error("Password reset failed with unexpected error", e);
            redirectAttributes.addFlashAttribute("error", "Failed to process password reset. Please try again.");
            return "redirect:/forgot-password";
        }
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        String forwardedProto = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getHeader("X-Forwarded-Scheme"));
        String forwardedHost = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));

        String scheme = forwardedProto != null ? forwardedProto.trim() : request.getScheme();
        String host = forwardedHost != null ? forwardedHost.trim() : request.getServerName();
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();

        if (host.contains(",")) {
            host = host.split(",")[0].trim();
        }

        if (host.contains(":")) {
            String hostOnly = host.substring(0, host.lastIndexOf(':'));
            String portPart = host.substring(host.lastIndexOf(':') + 1);
            if (isDefaultPort(scheme, portPart)) {
                return scheme + "://" + hostOnly + contextPath;
            }
            return scheme + "://" + host + contextPath;
        }

        int port = request.getServerPort();
        if (isDefaultPort(scheme, String.valueOf(port))) {
            return scheme + "://" + host + contextPath;
        }
        return scheme + "://" + host + ":" + port + contextPath;
    }

    private boolean isDefaultPort(String scheme, String portValue) {
        if (scheme == null || portValue == null) {
            return false;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return "80".equals(portValue);
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return "443".equals(portValue);
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
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
            
            if (password.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters long");
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
