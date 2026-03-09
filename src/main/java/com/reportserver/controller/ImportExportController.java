package com.reportserver.controller;

import com.reportserver.model.ReportTemplate;
import com.reportserver.service.ImportExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/import-export")
public class ImportExportController {
    private static final Logger logger = LoggerFactory.getLogger(ImportExportController.class);

    private final ImportExportService importExportService;

    @Value("${report.export.directory:./data/exports}")
    private String exportDir;

    public ImportExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * POST /api/import-export/export/{reportFileName} - Export a single report bundle
     */
    @PostMapping("/export/{reportFileName}")
    public ResponseEntity<Resource> exportReportBundle(
            @PathVariable String reportFileName,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            logger.info("User {} requesting export of report {}", userId, reportFileName);

            File zipFile = importExportService.exportReportBundle(reportFileName);

            Resource resource = new FileSystemResource(zipFile);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + zipFile.getName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(resource);

        } catch (Exception e) {
            logger.error("Failed to export report bundle: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * POST /api/import-export/export-multiple - Export multiple reports as single ZIP
     */
    @PostMapping("/export-multiple")
    public ResponseEntity<Resource> exportMultipleReports(
            @RequestBody Map<String, List<String>> request,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();
            List<String> reportFileNames = request.get("reportFileNames");

            if (reportFileNames == null || reportFileNames.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            logger.info("User {} requesting export of {} reports", userId, reportFileNames.size());

            File zipFile = importExportService.exportMultipleReports(reportFileNames);

            Resource resource = new FileSystemResource(zipFile);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + zipFile.getName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(resource);

        } catch (Exception e) {
            logger.error("Failed to export multiple reports: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * POST /api/import-export/import - Import a report bundle from ZIP file
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importReportBundle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            Authentication auth) {
        try {
            String userId = auth.getPrincipal().toString();

            if (!file.getOriginalFilename().endsWith(".zip")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "File must be a ZIP archive"));
            }

            // Save uploaded file temporarily
            File tempFile = File.createTempFile("import_", ".zip");
            file.transferTo(tempFile);

            logger.info("User {} importing report bundle from {}", userId, file.getOriginalFilename());

            // Import the bundle
            ReportTemplate imported = importExportService.importReportBundle(tempFile, userId, workspaceId);

            // Clean up temp file
            tempFile.delete();

            Map<String, Object> response = new HashMap<>();
            response.put("id", imported.getId());
            response.put("reportFileName", imported.getReportFileName());
            response.put("displayName", imported.getDisplayName());
            response.put("category", imported.getCategory());
            response.put("tags", imported.getTags());
            response.put("message", "Report bundle imported successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to import report bundle: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to import report bundle: " + e.getMessage()));
        }
    }

    /**
     * GET /api/import-export/export-status - Check export file status
     */
    @GetMapping("/export-status")
    public ResponseEntity<Map<String, Object>> getExportStatus() {
        try {
            File exportDirectory = new File(exportDir);
            if (!exportDirectory.exists()) {
                return ResponseEntity.ok(Map.of(
                    "exportDir", exportDir,
                    "isReady", false,
                    "message", "Export directory does not exist yet"
                ));
            }

            File[] files = exportDirectory.listFiles();
            long totalSize = 0;
            int fileCount = 0;

            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".zip")) {
                        fileCount++;
                        totalSize += f.length();
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "exportDir", exportDir,
                "isReady", true,
                "fileCount", fileCount,
                "totalSizeMB", totalSize / (1024.0 * 1024.0)
            ));

        } catch (Exception e) {
            logger.error("Failed to check export status: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to check export status"));
        }
    }
}
