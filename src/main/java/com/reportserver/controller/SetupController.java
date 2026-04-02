package com.reportserver.controller;

import com.reportserver.model.User;
import com.reportserver.service.EmailNotificationService;
import com.reportserver.service.InstallationService;
import com.reportserver.service.SmtpSettingsService;
import com.reportserver.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class SetupController {

    private final InstallationService installationService;
    private final UserService userService;
    private final SmtpSettingsService smtpSettingsService;
    private final EmailNotificationService emailNotificationService;

    public SetupController(InstallationService installationService,
                           UserService userService,
                           SmtpSettingsService smtpSettingsService,
                           EmailNotificationService emailNotificationService) {
        this.installationService = installationService;
        this.userService = userService;
        this.smtpSettingsService = smtpSettingsService;
        this.emailNotificationService = emailNotificationService;
    }

    @GetMapping("/setup")
    public String setupPage(Model model) {
        if (installationService.isSetupComplete()) {
            return "redirect:/login";
        }
        return "installation";
    }

    @GetMapping("/api/setup/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> setupStatus = installationService.getSetupStatus();
        if (Boolean.TRUE.equals(setupStatus.get("adminConfigured")) && !Boolean.TRUE.equals(setupStatus.get("setupCompleted"))) {
            userService.getAllUsers().stream().findFirst().ifPresent(user -> {
                if (user.isTwoFactorEnabled() && !user.isTwoFactorConfirmed()) {
                    try {
                        setupStatus.put("pendingTwoFactorSetup", userService.getTwoFactorSetupForUser(user.getUsername()));
                        setupStatus.put("pendingAdminUsername", user.getUsername());
                    } catch (Exception ignored) {
                        // If the pending 2FA setup cannot be reconstructed, the wizard can still continue without it.
                    }
                }
            });
        }
        response.put("status", "success");
        response.put("setup", setupStatus);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/setup/admin")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setupAdmin(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (installationService.isSetupComplete()) {
                response.put("status", "error");
                response.put("message", "Installation is already completed");
                return ResponseEntity.badRequest().body(response);
            }

            String username = asString(request.get("username"));
            String email = asString(request.get("email"));
            String password = asString(request.get("password"));
            String confirmPassword = asString(request.get("confirmPassword"));
            Object enableTwoFactorValue = request.get("enableTwoFactor");
            boolean enableTwoFactor = enableTwoFactorValue != null && Boolean.parseBoolean(String.valueOf(enableTwoFactorValue));

            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Passwords do not match");
            }

            User admin = userService.createInitialAdmin(username, email, password);
            installationService.markAdminConfigured();

            Map<String, Object> twoFactorSetup = null;
            if (enableTwoFactor) {
                twoFactorSetup = userService.enableTwoFactor(admin.getId());
            }

            try {
                emailNotificationService.sendWelcomeEmail(admin);
            } catch (Exception ex) {
                // Do not fail installation if the welcome email cannot be sent.
            }

            response.put("status", "success");
            response.put("message", enableTwoFactor
                    ? "Admin user configured successfully. Confirm the 2FA code to finish installation"
                    : "Admin user configured successfully");
            response.put("username", admin.getUsername());
            if (twoFactorSetup != null) {
                response.put("twoFactorSetup", twoFactorSetup);
                response.put("requiresTwoFactorConfirmation", true);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/setup/admin/confirm-2fa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmAdminTwoFactor(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (installationService.isSetupComplete()) {
                response.put("status", "error");
                response.put("message", "Installation is already completed");
                return ResponseEntity.badRequest().body(response);
            }

            String username = asString(request.get("username"));
            String code = asString(request.get("code"));

            if (username.isBlank()) {
                throw new IllegalArgumentException("Username is required");
            }

            boolean valid = userService.verifyTwoFactorForUser(username, code);
            if (!valid) {
                throw new IllegalArgumentException("Invalid authentication code");
            }

            installationService.markSetupCompleted();

            response.put("status", "success");
            response.put("message", "2FA confirmed and installation completed successfully");
            response.put("username", username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/setup/smtp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setupSmtp(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (installationService.isSetupComplete()) {
                response.put("status", "error");
                response.put("message", "Installation is already completed");
                return ResponseEntity.badRequest().body(response);
            }

            smtpSettingsService.saveSettings(request, "installer");
            installationService.markSmtpConfigured();

            response.put("status", "success");
            response.put("message", "SMTP configuration saved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/setup/smtp/skip")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> skipSmtp() {
        Map<String, Object> response = new HashMap<>();
        installationService.markSmtpConfigured();
        response.put("status", "success");
        response.put("message", "SMTP step skipped");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/setup/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeSetup() {
        Map<String, Object> response = new HashMap<>();
        try {
            installationService.markSetupCompleted();
            response.put("status", "success");
            response.put("message", "Installation completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private String asString(String value) {
        return value == null ? "" : value.trim();
    }
}
