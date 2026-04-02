package com.reportserver.controller;

import com.reportserver.service.EmailNotificationService;
import com.reportserver.service.SmtpSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class SmtpSettingsController {

    private final SmtpSettingsService smtpSettingsService;
    private final EmailNotificationService emailNotificationService;

    public SmtpSettingsController(SmtpSettingsService smtpSettingsService,
                                  EmailNotificationService emailNotificationService) {
        this.smtpSettingsService = smtpSettingsService;
        this.emailNotificationService = emailNotificationService;
    }

    @GetMapping("/settings/smtp")
    @PreAuthorize("hasRole('ADMIN')")
    public String smtpSettingsPage() {
        return "smtp-settings";
    }

    @GetMapping("/api/settings/smtp")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSmtpSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("status", "success");
            response.put("settings", smtpSettingsService.getSettingsForAdmin());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/settings/smtp")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSmtpSettings(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "system";

            smtpSettingsService.saveSettings(request, username);

            response.put("status", "success");
            response.put("message", "SMTP settings saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/settings/smtp/test")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String recipient = request.get("recipient") == null ? null : String.valueOf(request.get("recipient"));
            emailNotificationService.sendTestEmail(recipient);
            response.put("status", "success");
            response.put("message", "Test email sent successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
