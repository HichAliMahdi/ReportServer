package com.reportserver.controller;

import com.reportserver.model.ReportDeliveryOption;
import com.reportserver.service.ReportDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/delivery-options")
public class DeliveryOptionsController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryOptionsController.class);

    private final ReportDeliveryService deliveryService;

    public DeliveryOptionsController(ReportDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * GET /api/delivery-options?scheduleId={id} - List delivery options for a schedule
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDeliveryOptions(
            @RequestParam Long scheduleId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            List<ReportDeliveryOption> options = deliveryService.getDeliveryOptions(scheduleId);

            List<Map<String, Object>> content = new ArrayList<>();
            for (ReportDeliveryOption option : options) {
                content.add(buildDeliveryOptionDto(option));
            }

            return ResponseEntity.ok(Map.of(
                "scheduleId", scheduleId,
                "deliveryOptions", content,
                "count", content.size()
            ));

        } catch (Exception e) {
            logger.error("Failed to get delivery options: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get delivery options"));
        }
    }

    /**
     * POST /api/delivery-options - Create a new delivery option
     * Request body:
     * {
     *   "scheduleId": 1,
     *   "type": "EMAIL" or "WEBHOOK",
     *   "recipientOrUrl": "user@example.com,admin@example.com" or "https://webhook.example.com/reports",
     *   "metadata": "{...}"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createDeliveryOption(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            Long scheduleId = Long.parseLong(request.get("scheduleId"));
            String typeStr = request.get("type");
            String recipientOrUrl = request.get("recipientOrUrl");
            String metadata = request.getOrDefault("metadata", "");

            if (recipientOrUrl == null || recipientOrUrl.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "recipientOrUrl is required"));
            }

            ReportDeliveryOption.DeliveryType type = ReportDeliveryOption.DeliveryType.valueOf(typeStr);

            ReportDeliveryOption option = deliveryService.createDeliveryOption(
                scheduleId, type, recipientOrUrl, metadata
            );

            logger.info("Created {} delivery option for schedule {} by user {}",
                type, scheduleId, userId);

            return ResponseEntity.ok(buildDeliveryOptionDto(option));

        } catch (Exception e) {
            logger.error("Failed to create delivery option: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(Map.of("error", "Failed to create delivery option: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/delivery-options/{deliveryId} - Update delivery option
     */
    @PutMapping("/{deliveryId}")
    public ResponseEntity<Map<String, Object>> updateDeliveryOption(
            @PathVariable Long deliveryId,
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            Boolean enabled = (Boolean) request.get("enabled");
            String recipientOrUrl = (String) request.get("recipientOrUrl");

            ReportDeliveryOption updated = deliveryService.updateDeliveryOption(
                deliveryId, enabled, recipientOrUrl
            );

            logger.info("Updated delivery option {} by user {}", deliveryId, userId);
            return ResponseEntity.ok(buildDeliveryOptionDto(updated));

        } catch (Exception e) {
            logger.error("Failed to update delivery option: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(Map.of("error", "Failed to update delivery option: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/delivery-options/{deliveryId} - Delete delivery option
     */
    @DeleteMapping("/{deliveryId}")
    public ResponseEntity<Map<String, String>> deleteDeliveryOption(
            @PathVariable Long deliveryId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            deliveryService.deleteDeliveryOption(deliveryId);

            logger.info("Deleted delivery option {} by user {}", deliveryId, userId);
            return ResponseEntity.ok(Map.of("message", "Delivery option deleted successfully"));

        } catch (Exception e) {
            logger.error("Failed to delete delivery option: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(Map.of("error", "Failed to delete delivery option"));
        }
    }

    private Map<String, Object> buildDeliveryOptionDto(ReportDeliveryOption option) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", option.getId());
        dto.put("scheduleId", option.getScheduleId());
        dto.put("type", option.getType());
        dto.put("recipientOrUrl", maskSensitiveData(option.getRecipientOrUrl()));
        dto.put("enabled", option.getEnabled());
        dto.put("createdAt", option.getCreatedAt());
        dto.put("updatedAt", option.getUpdatedAt());
        return dto;
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 10) {
            return data;
        }
        return data.substring(0, 5) + "...***";
    }
}
