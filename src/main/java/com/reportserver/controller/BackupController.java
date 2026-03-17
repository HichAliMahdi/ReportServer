package com.reportserver.controller;

import com.reportserver.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for manual backup trigger and backup listing.
 *
 * POST  /api/admin/backup    — trigger an immediate backup
 * GET   /api/admin/backups   — list existing backup directories
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    @Autowired
    private BackupService backupService;

    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> triggerBackup() {
        try {
            String path = backupService.performBackup();
            return ResponseEntity.ok(Map.of(
                    "status",  "success",
                    "message", "Backup created successfully",
                    "path",    path
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status",  "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/backups")
    public ResponseEntity<Map<String, Object>> listBackups() {
        try {
            List<String> backups = backupService.listBackups();
            return ResponseEntity.ok(Map.of(
                    "backups", backups,
                    "count",   backups.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status",  "error",
                    "message", e.getMessage()
            ));
        }
    }
}
