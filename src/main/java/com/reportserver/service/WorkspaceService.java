package com.reportserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.model.Workspace;
import com.reportserver.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkspaceService {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceService(WorkspaceRepository workspaceRepository, ObjectMapper objectMapper) {
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new workspace for a user
     */
    public Workspace createWorkspace(String name, String description, String ownerId) {
        try {
            // Check if workspace with same name already exists for this owner
            Optional<Workspace> existing = workspaceRepository.findByNameAndOwnerId(name, ownerId);
            if (existing.isPresent()) {
                throw new RuntimeException("Workspace '" + name + "' already exists for this user");
            }

            Workspace workspace = new Workspace(name, description, ownerId);
            Workspace saved = workspaceRepository.save(workspace);
            logger.info("Created workspace '{}' for user {}", name, ownerId);
            return saved;

        } catch (Exception e) {
            logger.error("Failed to create workspace: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create workspace", e);
        }
    }

    /**
     * Get all workspaces for a user
     */
    public List<Workspace> getUserWorkspaces(String userId) {
        return workspaceRepository.findByActiveTrueAndOwnerId(userId);
    }

    /**
     * Get user workspaces with pagination
     */
    public Page<Workspace> getUserWorkspacesPaginated(String userId, Pageable pageable) {
        return workspaceRepository.findByOwnerId(userId, pageable);
    }

    /**
     * Get a specific workspace
     */
    public Workspace getWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new RuntimeException("Workspace not found"));
    }

    /**
     * Check if user is member of workspace (and get role)
     */
    public boolean isMemberOfWorkspace(String userId, Long workspaceId) {
        Optional<Workspace> workspace = workspaceRepository.findById(workspaceId);
        if (workspace.isEmpty()) {
            return false;
        }

        // Check if owner
        if (workspace.get().getOwnerId().equals(userId)) {
            return true;
        }

        // Check if in members list
        try {
            List<Map<String, String>> members = parseMembersJson(workspace.get().getMembers());
            return members.stream().anyMatch(m -> userId.equals(m.get("userId")));
        } catch (Exception e) {
            logger.warn("Failed to parse members for workspace {}: {}", workspaceId, e.getMessage());
            return false;
        }
    }

    /**
     * Add a member to workspace
     */
    public void addMember(Long workspaceId, String userId, String role) {
        try {
            Workspace workspace = getWorkspace(workspaceId);

            List<Map<String, String>> members = parseMembersJson(workspace.getMembers());

            // Check if already member
            if (members.stream().anyMatch(m -> userId.equals(m.get("userId")))) {
                throw new RuntimeException("User already member of this workspace");
            }

            Map<String, String> newMember = new HashMap<>();
            newMember.put("userId", userId);
            newMember.put("role", role); // MEMBER, ADMIN
            members.add(newMember);

            workspace.setMembers(objectMapper.writeValueAsString(members));
            workspaceRepository.save(workspace);
            logger.info("Added user {} to workspace {} as {}", userId, workspaceId, role);

        } catch (Exception e) {
            logger.error("Failed to add member to workspace: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add member", e);
        }
    }

    /**
     * Remove a member from workspace
     */
    public void removeMember(Long workspaceId, String userId) {
        try {
            Workspace workspace = getWorkspace(workspaceId);

            List<Map<String, String>> members = parseMembersJson(workspace.getMembers());
            members.removeIf(m -> userId.equals(m.get("userId")));

            workspace.setMembers(objectMapper.writeValueAsString(members));
            workspaceRepository.save(workspace);
            logger.info("Removed user {} from workspace {}", userId, workspaceId);

        } catch (Exception e) {
            logger.error("Failed to remove member from workspace: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove member", e);
        }
    }

    /**
     * Update workspace metadata
     */
    public Workspace updateWorkspace(Long workspaceId, String name, String description) {
        try {
            Workspace workspace = getWorkspace(workspaceId);
            if (name != null) workspace.setName(name);
            if (description != null) workspace.setDescription(description);
            return workspaceRepository.save(workspace);

        } catch (Exception e) {
            logger.error("Failed to update workspace: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update workspace", e);
        }
    }

    /**
     * Deactivate a workspace
     */
    public void deactivateWorkspace(Long workspaceId) {
        try {
            Workspace workspace = getWorkspace(workspaceId);
            workspace.setActive(false);
            workspaceRepository.save(workspace);
            logger.info("Deactivated workspace {}", workspaceId);

        } catch (Exception e) {
            logger.error("Failed to deactivate workspace: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to deactivate workspace", e);
        }
    }

    private List<Map<String, String>> parseMembersJson(String membersJson) throws Exception {
        if (membersJson == null || membersJson.isEmpty()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(
            membersJson,
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {}
        );
    }
}
