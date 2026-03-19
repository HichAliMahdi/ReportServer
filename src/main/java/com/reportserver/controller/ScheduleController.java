package com.reportserver.controller;

import com.reportserver.dto.ScheduleDeliveryTestRequestDTO;
import com.reportserver.dto.ScheduledReportDTO;
import com.reportserver.service.ReportDeliveryService;
import com.reportserver.service.ReportSchedulerService;
import com.reportserver.service.ScheduledReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/schedules")
public class ScheduleController {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleController.class);

    @Autowired
    private ScheduledReportService scheduledReportService;

    @Autowired
    private ReportSchedulerService reportSchedulerService;

    @Autowired
    private ReportDeliveryService reportDeliveryService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    @GetMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllSchedules(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        try {
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

            int resolvedSize = size == null ? defaultPageSize : Math.min(size, maxPageSize);
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(resolvedSize, 1));

            Page<ScheduledReportDTO> schedules;
            if (isAdmin) {
                schedules = scheduledReportService.getAllScheduledReports(pageable);
            } else {
                schedules = scheduledReportService.getScheduledReportsByUser(authentication.getName(), pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", schedules.getContent());
            response.put("page", schedules.getNumber());
            response.put("size", schedules.getSize());
            response.put("totalElements", schedules.getTotalElements());
            response.put("totalPages", schedules.getTotalPages());
            response.put("first", schedules.isFirst());
            response.put("last", schedules.isLast());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching scheduled reports", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<ScheduledReportDTO> getScheduleById(@PathVariable Long id, Authentication authentication) {
        try {
            ScheduledReportDTO schedule = scheduledReportService.getScheduledReportById(id);
            
            if (schedule == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user has access (admin or owner)
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            boolean isOwner = schedule.getCreatedBy().equals(authentication.getName());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).build();
            }
            
            return ResponseEntity.ok(schedule);
        } catch (Exception e) {
            logger.error("Error fetching scheduled report: " + id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<ScheduledReportDTO> createSchedule(
            @RequestBody ScheduledReportDTO dto,
            Authentication authentication) {
        try {
            ScheduledReportDTO created = scheduledReportService.createScheduledReport(
                    dto, authentication.getName());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid scheduled report request", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error creating scheduled report", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<ScheduledReportDTO> updateSchedule(
            @PathVariable Long id,
            @RequestBody ScheduledReportDTO dto,
            Authentication authentication) {
        try {
            ScheduledReportDTO existing = scheduledReportService.getScheduledReportById(id);
            
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user has access (admin or owner)
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            boolean isOwner = existing.getCreatedBy().equals(authentication.getName());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).build();
            }
            
            ScheduledReportDTO updated = scheduledReportService.updateScheduledReport(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid scheduled report update request for id {}", id, e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error updating scheduled report: " + id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id, Authentication authentication) {
        try {
            ScheduledReportDTO existing = scheduledReportService.getScheduledReportById(id);
            
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user has access (admin or owner)
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            boolean isOwner = existing.getCreatedBy().equals(authentication.getName());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).build();
            }
            
            scheduledReportService.deleteScheduledReport(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error deleting scheduled report: " + id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Void> toggleSchedule(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication) {
        try {
            ScheduledReportDTO existing = scheduledReportService.getScheduledReportById(id);
            
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user has access (admin or owner)
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            boolean isOwner = existing.getCreatedBy().equals(authentication.getName());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).build();
            }
            
            scheduledReportService.toggleScheduledReport(id, enabled);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error toggling scheduled report: " + id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/{id}/execute")
    @ResponseBody
    public ResponseEntity<String> executeScheduleNow(@PathVariable Long id, Authentication authentication) {
        try {
            ScheduledReportDTO existing = scheduledReportService.getScheduledReportById(id);
            
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user has access (admin or owner)
            boolean isAdmin = authentication.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            boolean isOwner = existing.getCreatedBy().equals(authentication.getName());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).build();
            }
            
            reportSchedulerService.executeScheduledReportNow(id);
            return ResponseEntity.ok("Report execution started");
        } catch (Exception e) {
            logger.error("Error executing scheduled report: " + id, e);
            return ResponseEntity.status(500).body("Error executing report: " + e.getMessage());
        }
    }

    @PostMapping("/test-delivery")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testDelivery(
            @RequestBody ScheduleDeliveryTestRequestDTO request,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            String deliveryMethod = normalizeDeliveryMethod(request.getDeliveryMethod());
            String reportName = (request.getReportName() == null || request.getReportName().isBlank())
                    ? "report"
                    : request.getReportName().trim();
            String requestedBy = authentication != null ? authentication.getName() : "system";

            if ("FILE_SYSTEM".equals(deliveryMethod)) {
                response.put("status", "success");
                response.put("message", "File-system delivery does not require a test message.");
                return ResponseEntity.ok(response);
            }

            if ("EMAIL".equals(deliveryMethod)) {
                String recipients = request.getEmailRecipients() == null ? "" : request.getEmailRecipients().trim();
                if (recipients.isEmpty()) {
                    throw new IllegalArgumentException("Email recipients are required for email delivery test.");
                }
                reportDeliveryService.sendTestEmail(recipients, reportName, requestedBy);
                response.put("status", "success");
                response.put("message", "Test email sent successfully.");
                return ResponseEntity.ok(response);
            }

            String webhookUrl = request.getWebhookUrl() == null ? "" : request.getWebhookUrl().trim();
            validateWebhookUrl(webhookUrl);
            reportDeliveryService.sendTestWebhook(webhookUrl, reportName, requestedBy);
            response.put("status", "success");
            response.put("message", "Test webhook delivered successfully.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delivery test request", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error while sending test delivery", e);
            response.put("status", "error");
            response.put("message", "Failed to send test delivery: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private String normalizeDeliveryMethod(String deliveryMethod) {
        if (deliveryMethod == null || deliveryMethod.isBlank()) {
            throw new IllegalArgumentException("Delivery method is required.");
        }

        String normalized = deliveryMethod.trim().toUpperCase();
        if ("FILE_SYSTEM".equals(normalized)
                || "EMAIL".equals(normalized)
                || "WEBHOOK".equals(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException("Unsupported delivery method: " + deliveryMethod);
    }

    private void validateWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required for webhook delivery test.");
        }

        try {
            URI uri = URI.create(webhookUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null) {
                return;
            }
        } catch (Exception ignored) {
            // Validation handled below.
        }

        throw new IllegalArgumentException("Invalid webhook URL: " + webhookUrl);
    }
}
