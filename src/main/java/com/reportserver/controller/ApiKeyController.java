package com.reportserver.controller;

import com.reportserver.model.ApiKey;
import com.reportserver.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * GET /api/api-keys - List user's API keys (paginated)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listApiKeys(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            if (size > maxPageSize) size = maxPageSize;
            if (size < 1) size = defaultPageSize;

            Pageable pageable = PageRequest.of(page, size);
            Page<ApiKey> keys = apiKeyService.getUserKeys(userId, pageable);

            // Hide plainKey in responses (only shown during generation)
            List<Map<String, Object>> content = new ArrayList<>();
            for (ApiKey key : keys.getContent()) {
                Map<String, Object> keyDto = new HashMap<>();
                keyDto.put("id", key.getId());
                keyDto.put("name", key.getName());
                keyDto.put("createdAt", key.getCreatedAt());
                keyDto.put("lastUsedAt", key.getLastUsedAt());
                keyDto.put("active", key.getActive());
                keyDto.put("permissions", key.getPermissions());
                content.add(keyDto);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", keys.getTotalElements());
            response.put("totalPages", keys.getTotalPages());
            response.put("first", page == 0);
            response.put("last", keys.isLast());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to list API keys: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to list API keys"));
        }
    }

    /**
     * POST /api/api-keys - Generate a new API key (plaintext returned only once)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> generateApiKey(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            String keyName = request.getOrDefault("name", "API Key");
            String permissions = request.getOrDefault("permissions", 
                "[\"reports:read\",\"reports:generate\",\"schedules:view\"]");

            ApiKey apiKey = apiKeyService.generateNewKey(userId, keyName, permissions);

            Map<String, Object> response = new HashMap<>();
            response.put("id", apiKey.getId());
            response.put("name", apiKey.getName());
            response.put("plainKey", apiKey.getPlainKey()); // ⚠️ Shown only once
            response.put("message", "Save your API key in a secure location. It won't be shown again.");
            response.put("createdAt", apiKey.getCreatedAt());

            logger.info("Generated API key '{}' for user {}", keyName, userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to generate API key: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate API key"));
        }
    }

    /**
     * PUT /api/api-keys/{keyId} - Rename an API key
     */
    @PutMapping("/{keyId}")
    public ResponseEntity<Map<String, Object>> renameApiKey(
            @PathVariable Long keyId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            String newName = request.get("name");

            if (newName == null || newName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }

            ApiKey updated = apiKeyService.renameKey(userId, keyId, newName);

            Map<String, Object> response = new HashMap<>();
            response.put("id", updated.getId());
            response.put("name", updated.getName());
            response.put("message", "API key renamed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to rename API key: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/api-keys/{keyId} - Revoke an API key
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Map<String, String>> revokeApiKey(
            @PathVariable Long keyId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            apiKeyService.revokeKey(userId, keyId);

            return ResponseEntity.ok(Map.of("message", "API key revoked successfully"));

        } catch (Exception e) {
            logger.error("Failed to revoke API key: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}
