package com.reportserver.controller;

import com.reportserver.dto.DataSourceDTO;
import com.reportserver.model.DataSource;
import com.reportserver.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);
    private static final String DATA_FILES_DIR = "data/datasource_files/";

    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private com.reportserver.service.JRDataSourceProviderService jrDataSourceProviderService;

    /**
     * Get all datasources (without passwords)
     */
    @GetMapping
    public ResponseEntity<List<DataSourceDTO>> getAllDataSources() {
        try {
            List<DataSourceDTO> dataSources = dataSourceService.getAllDataSources()
                .stream()
                .map(DataSourceDTO::fromEntity)
                .collect(Collectors.toList());
            return ResponseEntity.ok(dataSources);
        } catch (Exception e) {
            logger.error("Error retrieving datasources", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get datasource by ID (without password)
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSourceDTO> getDataSourceById(@PathVariable Long id) {
        try {
            return dataSourceService.getDataSourceById(id)
                .map(DataSourceDTO::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error retrieving datasource with id: " + id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Create a new datasource
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createDataSource(@RequestBody DataSource dataSource) {
        Map<String, Object> response = new HashMap<>();
        try {
            DataSource created = dataSourceService.createDataSource(dataSource);
            response.put("status", "success");
            response.put("message", "Datasource created successfully");
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid datasource data", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error creating datasource", e);
            response.put("status", "error");
            response.put("message", "Failed to create datasource: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Update an existing datasource
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDataSource(
            @PathVariable Long id,
            @RequestBody DataSource dataSource) {
        Map<String, Object> response = new HashMap<>();
        try {
            DataSource updated = dataSourceService.updateDataSource(id, dataSource);
            response.put("status", "success");
            response.put("message", "Datasource updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid datasource data", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error updating datasource", e);
            response.put("status", "error");
            response.put("message", "Failed to update datasource: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Delete a datasource
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDataSource(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            dataSourceService.deleteDataSource(id);
            response.put("status", "success");
            response.put("message", "Datasource deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Datasource not found", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error deleting datasource", e);
            response.put("status", "error");
            response.put("message", "Failed to delete datasource: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Test datasource connection
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody DataSource dataSource) {
        Map<String, Object> response = new HashMap<>();
        try {
            // If datasource has an ID and password is empty, get the existing password from DB
            if (dataSource.getId() != null && 
                (dataSource.getPassword() == null || dataSource.getPassword().trim().isEmpty())) {
                dataSourceService.getDataSourceById(dataSource.getId()).ifPresent(existing -> {
                    dataSource.setPassword(existing.getPassword());
                });
            }
            
            boolean isConnected = dataSourceService.testConnection(dataSource);
            response.put("status", isConnected ? "success" : "failed");
            response.put("message", isConnected ? 
                "Connection successful" : 
                "Connection failed - please check your credentials and database URL");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error testing connection", e);
            response.put("status", "error");
            response.put("message", "Error testing connection: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Upload a data file (CSV, XML, JSON) for file-based datasources
     */
    @PostMapping("/upload-file")
    public ResponseEntity<Map<String, Object>> uploadDataFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Please select a file to upload");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null) {
                throw new IllegalArgumentException("Invalid filename");
            }
            
            // Validate file type
            String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            if (!extension.matches("csv|xml|json")) {
                throw new IllegalArgumentException("Only CSV, XML, and JSON files are supported");
            }
            
            // Ensure directory exists
            jrDataSourceProviderService.ensureDataFilesDirectoryExists();
            
            // Save file
            java.nio.file.Path path = java.nio.file.Paths.get("data/datasource_files/" + filename);
            java.nio.file.Files.write(path, file.getBytes());
            
            logger.info("Data file uploaded successfully: {}", filename);
            response.put("status", "success");
            response.put("message", "File uploaded successfully");
            response.put("filename", filename);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading data file", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Execute a query on a datasource and return results
     */
    @PostMapping("/{id}/query")
    public ResponseEntity<Map<String, Object>> executeQuery(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "Query is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Get max rows parameter (default 1000)
            int maxRows = 1000;
            String maxRowsStr = request.get("maxRows");
            if (maxRowsStr != null && !maxRowsStr.isEmpty()) {
                try {
                    maxRows = Integer.parseInt(maxRowsStr);
                    if (maxRows < 1) maxRows = 1;
                    if (maxRows > 10000) maxRows = 10000; // Cap at 10k rows
                } catch (NumberFormatException e) {
                    maxRows = 1000;
                }
            }

            // Get datasource
            Optional<DataSource> dataSourceOpt = dataSourceService.getDataSourceById(id);
            if (!dataSourceOpt.isPresent()) {
                response.put("status", "error");
                response.put("message", "Datasource not found");
                return ResponseEntity.status(404).body(response);
            }
            
            DataSource dataSource = dataSourceOpt.get();

            // Execute query and get results
            Map<String, Object> results = dataSourceService.executeQuery(dataSource, query, maxRows);
            
            response.put("status", "success");
            response.put("columns", results.get("columns"));
            response.put("rows", results.get("rows"));
            response.put("rowCount", results.get("rowCount"));
            response.put("executionTime", results.get("executionTime"));
            response.put("truncated", results.get("truncated"));
            response.put("maxRows", maxRows);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing query", e);
            response.put("status", "error");
            response.put("message", "Query execution failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }}