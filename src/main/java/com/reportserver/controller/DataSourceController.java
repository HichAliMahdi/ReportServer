package com.reportserver.controller;

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

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);

    @Autowired
    private DataSourceService dataSourceService;

    /**
     * Get all datasources
     */
    @GetMapping
    public ResponseEntity<List<DataSource>> getAllDataSources() {
        try {
            List<DataSource> dataSources = dataSourceService.getAllDataSources();
            return ResponseEntity.ok(dataSources);
        } catch (Exception e) {
            logger.error("Error retrieving datasources", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get datasource by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSource> getDataSourceById(@PathVariable Long id) {
        try {
            return dataSourceService.getDataSourceById(id)
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
}
