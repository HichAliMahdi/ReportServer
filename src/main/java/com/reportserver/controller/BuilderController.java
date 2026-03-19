package com.reportserver.controller;

import com.reportserver.dto.BuilderGenerateRequestDTO;
import com.reportserver.service.BuilderAssetService;
import com.reportserver.service.BuilderGenerationService;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.SchemaIntrospectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/builder")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class BuilderController {
    
    private static final Logger logger = LoggerFactory.getLogger(BuilderController.class);
    
    @Value("${reportserver.upload.dir:data/reports/}")
    private String uploadDir;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private SchemaIntrospectionService schemaIntrospectionService;

    @Autowired
    private BuilderGenerationService builderGenerationService;

    @Autowired
    private BuilderAssetService builderAssetService;

    /**
     * Get all tables from a datasource
     */
    @GetMapping("/datasources/{datasourceId}/tables")
    @ResponseBody
    public ResponseEntity<?> getTables(@PathVariable Long datasourceId) {
        try {
            logger.info("Fetching tables for datasource ID: {}", datasourceId);
            
            try (Connection connection = dataSourceService.getConnection(datasourceId)) {
                if (connection == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Failed to connect to datasource"));
                }

                List<String> tables = schemaIntrospectionService.getTables(connection);
                return ResponseEntity.ok(Map.of("success", true, "tables", tables));
            }
            
        } catch (Exception e) {
            logger.error("Error fetching tables", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Get all columns for a specific table
     */
    @GetMapping("/datasources/{datasourceId}/tables/{tableName}/columns")
    @ResponseBody
    public ResponseEntity<?> getColumns(
            @PathVariable Long datasourceId,
            @PathVariable String tableName) {
        
        try {
            logger.info("Fetching columns for table {} from datasource ID: {}", tableName, datasourceId);
            
            try (Connection connection = dataSourceService.getConnection(datasourceId)) {
                if (connection == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Failed to connect to datasource"));
                }

                List<Map<String, String>> columns = schemaIntrospectionService.getColumns(connection, tableName);
                return ResponseEntity.ok(Map.of("success", true, "columns", columns));
            }
            
        } catch (Exception e) {
            logger.error("Error fetching columns", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Generate a JRXML file based on selected table, columns, parameters, and variables
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<?> generateReport(
            @Valid @ModelAttribute BuilderGenerateRequestDTO request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
        }

        return builderGenerationService.generateReport(request);
    }

    /**
     * Generate JRXML and immediately generate the report output.
     */
    @PostMapping("/generate-and-report")
    @ResponseBody
    public ResponseEntity<?> generateAndReport(
            @Valid @ModelAttribute BuilderGenerateRequestDTO request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
        }

        return builderGenerationService.generateAndReport(request);
    }

    /**
     * Generate report output directly without persisting the JRXML template.
     */
    @PostMapping("/generate-report-only")
    @ResponseBody
    public ResponseEntity<?> generateReportOnly(
            @Valid @ModelAttribute BuilderGenerateRequestDTO request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
        }

        return builderGenerationService.generateReportOnly(request);
    }

    /**
     * Download a generated JRXML file
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadJrxml(@PathVariable String fileName) {
        try {
            // Validate file name to prevent directory traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            File file = new File(this.uploadDir + fileName);
            
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
                
        } catch (Exception e) {
            logger.error("Error downloading file: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload an image for use in visual reports (logos, backgrounds, etc.)
     */
    @PostMapping("/upload-image")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        return builderAssetService.uploadImage(file);
    }

    /**
     * Upload a cover page file and return image data that can be previewed and injected in JRXML.
     * Supported formats: PDF (first page), PNG, JPG, JPEG, WEBP, GIF, BMP.
     */
    @PostMapping("/upload-cover-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadCoverFile(@RequestParam("file") MultipartFile file) {
        return builderAssetService.uploadCoverFile(file);
    }

    /**
     * Get list of all uploaded images
     */
    @GetMapping("/images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listImages() {
        return builderAssetService.listImages();
    }

    /**
     * Delete an image
     */
    @DeleteMapping("/images/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteImage(@PathVariable String fileName) {
        return builderAssetService.deleteImage(fileName);
    }

    /**
     * Save a visual report template (design configuration as JSON)
     */
    @PostMapping("/templates/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTemplate(@RequestBody Map<String, Object> templateData) {
        return builderAssetService.saveTemplate(templateData);
    }

    /**
     * Get list of all templates
     */
    @GetMapping("/templates")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listTemplates() {
        return builderAssetService.listTemplates();
    }

    /**
     * Load a specific template
     */
    @GetMapping("/templates/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> loadTemplate(@PathVariable String fileName) {
        return builderAssetService.loadTemplate(fileName);
    }

    /**
     * Delete a template
     */
    @DeleteMapping("/templates/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable String fileName) {
        return builderAssetService.deleteTemplate(fileName);
    }

    /**
     * Generate JRXML from visual builder design
     */
    @PostMapping("/visual/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateFromVisualDesign(@RequestBody Map<String, Object> designData) {
        return builderGenerationService.generateFromVisualDesign(designData);
    }

    /**
     * Generate visual JRXML and immediately generate the report output.
     */
    @PostMapping("/visual/generate-and-report")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateFromVisualDesignAndReport(@RequestBody Map<String, Object> designData) {
        return builderGenerationService.generateFromVisualDesignAndReport(designData);
    }

    /**
     * Generate report output from visual design without saving JRXML template.
     */
    @PostMapping("/visual/generate-report-only")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateFromVisualDesignReportOnly(@RequestBody Map<String, Object> designData) {
        return builderGenerationService.generateFromVisualDesignReportOnly(designData);
    }

}

