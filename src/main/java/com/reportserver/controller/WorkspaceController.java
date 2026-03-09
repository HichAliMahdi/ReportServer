package com.reportserver.controller;

import com.reportserver.model.Workspace;
import com.reportserver.service.WorkspaceService;
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
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;

    @Value("${reportserver.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${reportserver.pagination.max-page-size:200}")
    private int maxPageSize;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * GET /api/workspaces - List user's workspaces
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            if (size > maxPageSize) size = maxPageSize;
            if (size < 1) size = defaultPageSize;

            Pageable pageable = PageRequest.of(page, size);
            Page<Workspace> workspaces = workspaceService.getUserWorkspacesPaginated(userId, pageable);

            List<Map<String, Object>> content = new ArrayList<>();
            for (Workspace ws : workspaces.getContent()) {
                content.add(buildWorkspaceDto(ws));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", workspaces.getTotalElements());
            response.put("totalPages", workspaces.getTotalPages());
            response.put("first", page == 0);
            response.put("last", workspaces.isLast());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to list workspaces: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to list workspaces"));
        }
    }

    /**
     * POST /api/workspaces - Create a new workspace
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWorkspace(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            String name = request.getOrDefault("name", "New Workspace");
            String description = request.getOrDefault("description", "");

            Workspace workspace = workspaceService.createWorkspace(name, description, userId);

            logger.info("Created workspace '{}' for user {}", name, userId);
            return ResponseEntity.ok(buildWorkspaceDto(workspace));

        } catch (Exception e) {
            logger.error("Failed to create workspace: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/workspaces/{workspaceId} - Get workspace details
     */
    @GetMapping("/{workspaceId}")
    public ResponseEntity<Map<String, Object>> getWorkspace(
            @PathVariable Long workspaceId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            // Check authorization
            Workspace workspace = workspaceService.getWorkspace(workspaceId);
            if (!workspaceService.isMemberOfWorkspace(userId, workspaceId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(buildWorkspaceDto(workspace));

        } catch (Exception e) {
            logger.error("Failed to get workspace: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", "Workspace not found"));
        }
    }

    /**
     * PUT /api/workspaces/{workspaceId} - Update workspace
     */
    @PutMapping("/{workspaceId}")
    public ResponseEntity<Map<String, Object>> updateWorkspace(
            @PathVariable Long workspaceId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            // Check authorization (owner or admin only)
            Workspace workspace = workspaceService.getWorkspace(workspaceId);
            if (!workspace.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only owner can update workspace"));
            }

            String name = request.get("name");
            String description = request.get("description");

            Workspace updated = workspaceService.updateWorkspace(workspaceId, name, description);
            logger.info("Updated workspace {} by user {}", workspaceId, userId);

            return ResponseEntity.ok(buildWorkspaceDto(updated));

        } catch (Exception e) {
            logger.error("Failed to update workspace: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/workspaces/{workspaceId}/members - Add member to workspace
     */
    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<Map<String, String>> addMember(
            @PathVariable Long workspaceId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            // Check authorization (owner only)
            Workspace workspace = workspaceService.getWorkspace(workspaceId);
            if (!workspace.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only owner can add members"));
            }

            String memberUserId = request.get("userId");
            String role = request.getOrDefault("role", "MEMBER");

            workspaceService.addMember(workspaceId, memberUserId, role);
            logger.info("Added user {} to workspace {} as {}", memberUserId, workspaceId, role);

            return ResponseEntity.ok(Map.of("message", "Member added successfully"));

        } catch (Exception e) {
            logger.error("Failed to add member: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/workspaces/{workspaceId}/members/{memberId} - Remove member
     */
    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long workspaceId,
            @PathVariable String memberId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            // Check authorization (owner only)
            Workspace workspace = workspaceService.getWorkspace(workspaceId);
            if (!workspace.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only owner can remove members"));
            }

            workspaceService.removeMember(workspaceId, memberId);
            logger.info("Removed user {} from workspace {}", memberId, workspaceId);

            return ResponseEntity.ok(Map.of("message", "Member removed successfully"));

        } catch (Exception e) {
            logger.error("Failed to remove member: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/workspaces/{workspaceId} - Deactivate workspace
     */
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Map<String, String>> deactivateWorkspace(
            @PathVariable Long workspaceId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            // Check authorization (owner only)
            Workspace workspace = workspaceService.getWorkspace(workspaceId);
            if (!workspace.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only owner can deactivate workspace"));
            }

            workspaceService.deactivateWorkspace(workspaceId);
            logger.info("Deactivated workspace {} by user {}", workspaceId, userId);

            return ResponseEntity.ok(Map.of("message", "Workspace deactivated successfully"));

        } catch (Exception e) {
            logger.error("Failed to deactivate workspace: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildWorkspaceDto(Workspace workspace) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", workspace.getId());
        dto.put("name", workspace.getName());
        dto.put("description", workspace.getDescription());
        dto.put("ownerId", workspace.getOwnerId());
        dto.put("createdAt", workspace.getCreatedAt());
        dto.put("updatedAt", workspace.getUpdatedAt());
        dto.put("active", workspace.getActive());
        dto.put("members", workspace.getMembers());
        return dto;
    }
}
