package com.reportserver.controller;

import com.reportserver.dto.ParameterDTO;
import com.reportserver.dto.VariableDTO;
import com.reportserver.service.DataSourceService;
import com.reportserver.service.JrxmlBuilderService;
import com.reportserver.service.SchemaIntrospectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/builder")
public class BuilderController {
    
    private static final Logger logger = LoggerFactory.getLogger(BuilderController.class);
    private static final String UPLOAD_DIR = "data/reports/";

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private SchemaIntrospectionService schemaIntrospectionService;

    @Autowired
    private JrxmlBuilderService jrxmlBuilderService;

    /**
     * Get all tables from a datasource
     */
    @GetMapping("/datasources/{datasourceId}/tables")
    @ResponseBody
    public ResponseEntity<?> getTables(@PathVariable Long datasourceId) {
        Connection connection = null;
        try {
            logger.info("Fetching tables for datasource ID: {}", datasourceId);
            
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<String> tables = schemaIntrospectionService.getTables(connection);
            
            return ResponseEntity.ok(Map.of("success", true, "tables", tables));
            
        } catch (Exception e) {
            logger.error("Error fetching tables", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
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
        
        Connection connection = null;
        try {
            logger.info("Fetching columns for table {} from datasource ID: {}", tableName, datasourceId);
            
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<Map<String, String>> columns = schemaIntrospectionService.getColumns(connection, tableName);
            
            return ResponseEntity.ok(Map.of("success", true, "columns", columns));
            
        } catch (Exception e) {
            logger.error("Error fetching columns", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Generate a JRXML file based on selected table, columns, parameters, and variables
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<?> generateReport(
            @RequestParam String reportName,
            @RequestParam String tableName,
            @RequestParam List<String> columns,
            @RequestParam Long datasourceId,
            @RequestParam(required = false) String parametersJson,
            @RequestParam(required = false) String variablesJson) {
        
        Connection connection = null;
        try {
            logger.info("Generating JRXML for table {} with {} columns", tableName, columns.size());
            
            // Validate inputs
            if (reportName == null || reportName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Report name is required"));
            }
            
            if (!reportName.endsWith(".jrxml")) {
                reportName += ".jrxml";
            }
            
            if (columns == null || columns.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "At least one column must be selected"));
            }

            // Get column details from database
            connection = dataSourceService.getConnection(datasourceId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Failed to connect to datasource"));
            }

            List<Map<String, String>> allColumns = schemaIntrospectionService.getColumns(connection, tableName);
            
            // Filter to only selected columns
            List<Map<String, String>> selectedColumns = new ArrayList<>();
            for (String columnName : columns) {
                for (Map<String, String> col : allColumns) {
                    if (col.get("name").equals(columnName)) {
                        selectedColumns.add(col);
                        break;
                    }
                }
            }

            // Parse parameters from JSON if provided
            List<ParameterDTO> parameters = new ArrayList<>();
            if (parametersJson != null && !parametersJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    parameters = objectMapper.readValue(parametersJson, new TypeReference<List<ParameterDTO>>() {});
                    logger.info("Parsed {} parameter(s) from JSON", parameters.size());
                } catch (Exception e) {
                    logger.error("Error parsing parameters JSON", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid parameters format: " + e.getMessage()));
                }
            }

            // Parse variables from JSON if provided
            List<VariableDTO> variables = new ArrayList<>();
            if (variablesJson != null && !variablesJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    variables = objectMapper.readValue(variablesJson, new TypeReference<List<VariableDTO>>() {});
                    logger.info("Parsed {} variable(s) from JSON", variables.size());
                } catch (Exception e) {
                    logger.error("Error parsing variables JSON", e);
                    return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid variables format: " + e.getMessage()));
                }
            }

            // Generate JRXML content
            String jrxmlContent = jrxmlBuilderService.generateJrxml(
                reportName.replace(".jrxml", ""), 
                tableName, 
                selectedColumns,
                parameters,
                variables
            );

            // Save to file
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File jrxmlFile = new File(UPLOAD_DIR + reportName);
            try (FileWriter writer = new FileWriter(jrxmlFile)) {
                writer.write(jrxmlContent);
            }

            logger.info("Successfully generated JRXML file: {}", reportName);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Report generated successfully",
                "reportName", reportName
            ));
            
        } catch (Exception e) {
            logger.error("Error generating JRXML", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
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

            File file = new File(UPLOAD_DIR + fileName);
            
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
}
